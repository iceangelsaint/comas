package se.unlogic.eagledns;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.xbill.DNS.Address;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;
import se.unlogic.eagledns.plugins.Plugin;
import se.unlogic.eagledns.resolvers.Resolver;
import se.unlogic.eagledns.zoneproviders.ZoneProvider;
import se.unlogic.standardutils.datatypes.SimpleEntry;
import se.unlogic.standardutils.numbers.LongCounter;
import se.unlogic.standardutils.reflection.ReflectionUtils;
import se.unlogic.standardutils.settings.SettingNode;
import se.unlogic.standardutils.string.StringUtils;
import se.unlogic.standardutils.timer.RunnableTimerTask;
import se.unlogic.standardutils.xml.XMLParser;

public class EagleDNS implements Runnable, SystemInterface {
   public static final String VERSION_PREFIX = "Eagle DNS 1.1.2";
   public static final String VERSION;
   public final long startTime;
   public static final int FLAG_DNSSECOK = 1;
   public static final int FLAG_SIGONLY = 2;
   private final Logger log;
   private ConcurrentHashMap primaryZoneMap;
   private ConcurrentHashMap secondaryZoneMap;
   private final HashMap TSIGs;
   private final HashMap zoneProviders;
   private final ArrayList resolvers;
   private final HashMap plugins;
   private int tcpThreadPoolMinSize;
   private int tcpThreadPoolMaxSize;
   private int udpThreadPoolMinSize;
   private int udpThreadPoolMaxSize;
   private int tcpThreadPoolShutdownTimeout;
   private int udpThreadPoolShutdownTimeout;
   private ArrayList tcpMonitorThreads;
   private ArrayList udpMonitorThreads;
   private ThreadPoolExecutor tcpThreadPool;
   private ThreadPoolExecutor udpThreadPool;
   private LongCounter rejectedTCPConnections;
   private LongCounter rejectedUDPConnections;
   private int axfrTimeout;
   private Timer secondaryZoneUpdateTimer;
   private RunnableTimerTask timerTask;
   private Status status;
   private int defaultResponse;

   static {
      String tempVersion;
      try {
         tempVersion = "Eagle DNS 1.1.2 (rev. " + StringUtils.readStreamAsString(EagleDNS.class.getResourceAsStream("/META-INF/eagledns-svnrevision.txt")) + ")";
      } catch (Exception var2) {
         tempVersion = "Eagle DNS 1.1.2.unknown";
      }

      VERSION = tempVersion;
   }

   public EagleDNS(String configFilePath) throws UnknownHostException {
      this(configFilePath, "/conf/log4j.xml");
   }

   public EagleDNS(String configFilePath, String log4jPath) throws UnknownHostException {
      this.log = Logger.getLogger(this.getClass());
      this.primaryZoneMap = new ConcurrentHashMap();
      this.secondaryZoneMap = new ConcurrentHashMap();
      this.TSIGs = new HashMap();
      this.zoneProviders = new HashMap();
      this.resolvers = new ArrayList();
      this.plugins = new HashMap();
      this.tcpThreadPoolMinSize = 10;
      this.tcpThreadPoolMaxSize = 50;
      this.udpThreadPoolMinSize = 10;
      this.udpThreadPoolMaxSize = 50;
      this.tcpThreadPoolShutdownTimeout = 60;
      this.udpThreadPoolShutdownTimeout = 60;
      this.tcpMonitorThreads = new ArrayList();
      this.udpMonitorThreads = new ArrayList();
      this.rejectedTCPConnections = new LongCounter();
      this.rejectedUDPConnections = new LongCounter();
      this.axfrTimeout = 60;
      this.status = Status.STARTING;
      this.startTime = System.currentTimeMillis();
      DOMConfigurator.configure(log4jPath);
      System.out.println(VERSION + " starting...");
      this.log.fatal(VERSION + " starting...");

      XMLParser configFile;
      try {
         this.log.debug("Parsing config file...");
         configFile = new XMLParser(configFilePath);
      } catch (Exception var49) {
         this.log.fatal("Unable to open config file " + configFilePath + ", aborting startup!");
         System.out.println("Unable to open config file " + configFilePath + ", aborting startup!");
         return;
      }

      boolean requireZones = configFile.getBoolean("/Config/System/RequireZones");
      String defaultResponse = configFile.getString("/Config/System/DefaultResponse");
      if (defaultResponse.equalsIgnoreCase("NOERROR")) {
         this.defaultResponse = 0;
      } else {
         if (!defaultResponse.equalsIgnoreCase("NXDOMAIN")) {
            if (StringUtils.isEmpty(defaultResponse)) {
               this.log.fatal("No default response found, aborting startup!");
               System.out.println("No default response found, aborting startup!");
               return;
            }

            this.log.fatal("Invalid default response '" + defaultResponse + "' found, aborting startup!");
            System.out.println("Invalid default response '" + defaultResponse + "' found, aborting startup!");
            return;
         }

         this.defaultResponse = 3;
      }

      List<Integer> ports = configFile.getIntegers("/Config/System/Port");
      if (ports.isEmpty()) {
         this.log.debug("No ports found in config file " + configFilePath + ", using default port 53");
         ports.add(53);
      }

      List<InetAddress> addresses = new ArrayList();
      List<String> addressStrings = configFile.getStrings("/Config/System/Address");
      if (addressStrings != null && addressStrings != null) {
         for(String addressString : addressStrings) {
            try {
               addresses.add(Address.getByAddress(addressString));
            } catch (UnknownHostException e) {
               this.log.error("Invalid address " + addressString + " specified in config file, skipping address " + e);
            }
         }

         if (addresses.isEmpty()) {
            this.log.fatal("None of the " + addressStrings.size() + " addresses specified in the config file are valid, aborting startup!\n" + "Correct the addresses or remove them from the config file if you want to listen on all interfaces.");
            System.out.println("None of the " + addressStrings.size() + " addresses specified in the config file are valid, aborting startup!\n" + "Correct the addresses or remove them from the config file if you want to listen on all interfaces.");
         }
      } else {
         this.log.debug("No addresses found in config, listening on all addresses (0.0.0.0)");
         addresses.add(Address.getByAddress("0.0.0.0"));
      }

      Integer tcpThreadPoolMinSize = configFile.getInteger("/Config/System/TCPThreadPoolMinSize");
      if (tcpThreadPoolMinSize != null) {
         this.log.debug("Setting TCP thread pool min size to " + tcpThreadPoolMinSize);
         this.tcpThreadPoolMinSize = tcpThreadPoolMinSize;
      }

      Integer tcpThreadPoolMaxSize = configFile.getInteger("/Config/System/TCPThreadPoolMaxSize");
      if (tcpThreadPoolMaxSize != null) {
         this.log.debug("Setting TCP thread pool max size to " + tcpThreadPoolMaxSize);
         this.tcpThreadPoolMaxSize = tcpThreadPoolMaxSize;
      }

      Integer tcpThreadPoolShutdownTimeout = configFile.getInteger("/Config/System/TCPThreadPoolShutdownTimeout");
      if (tcpThreadPoolShutdownTimeout != null) {
         this.log.debug("Setting TCP thread pool shutdown timeout to " + tcpThreadPoolShutdownTimeout + " seconds");
         this.tcpThreadPoolShutdownTimeout = tcpThreadPoolShutdownTimeout;
      }

      Integer udpThreadPoolMinSize = configFile.getInteger("/Config/System/UDPThreadPoolMinSize");
      if (udpThreadPoolMinSize != null) {
         this.log.debug("Setting UDP thread pool min size to " + udpThreadPoolMinSize);
         this.udpThreadPoolMinSize = udpThreadPoolMinSize;
      }

      Integer udpThreadPoolMaxSize = configFile.getInteger("/Config/System/UDPThreadPoolMaxSize");
      if (udpThreadPoolMaxSize != null) {
         this.log.debug("Setting UDP thread pool max size to " + udpThreadPoolMaxSize);
         this.udpThreadPoolMaxSize = udpThreadPoolMaxSize;
      }

      Integer udpThreadPoolShutdownTimeout = configFile.getInteger("/Config/System/UDPThreadPoolShutdownTimeout");
      if (udpThreadPoolShutdownTimeout != null) {
         this.log.debug("Setting UDP thread pool shutdown timeout to " + udpThreadPoolShutdownTimeout + " seconds");
         this.udpThreadPoolShutdownTimeout = udpThreadPoolShutdownTimeout;
      }

      Integer axfrTimeout = configFile.getInteger("/Config/System/AXFRTimeout");
      if (axfrTimeout != null) {
         this.log.debug("Setting AXFR timeout to " + axfrTimeout);
         this.axfrTimeout = axfrTimeout;
      }

      for(SettingNode settingNode : configFile.getNodes("/Config/ZoneProviders/ZoneProvider")) {
         String name = settingNode.getString("Name");
         if (StringUtils.isEmpty(name)) {
            this.log.error("ZoneProvider element with no name set found in config, ignoring element.");
         } else {
            String className = settingNode.getString("Class");
            if (StringUtils.isEmpty(className)) {
               this.log.error("ZoneProvider element with no class set found in config, ignoring element.");
            } else {
               try {
                  this.log.debug("Instantiating zone provider " + name + " (" + className + ")");
                  ZoneProvider zoneProvider = (ZoneProvider)Class.forName(className).getDeclaredConstructor().newInstance();
                  this.log.debug("Zone provider " + name + " successfully instantiated");

                  for(SettingNode propertyElement : settingNode.getNodes("Properties/Property")) {
                     String propertyName = propertyElement.getString("@name");
                     if (StringUtils.isEmpty(propertyName)) {
                        this.log.error("Property element with no name set found in config for zone provider " + name + ", ignoring element");
                     } else {
                        String value = propertyElement.getString(".");
                        this.log.debug("Found value " + value + " for property " + propertyName);

                        try {
                           Method method = zoneProvider.getClass().getMethod("set" + StringUtils.toFirstLetterUppercase(propertyName), String.class);
                           ReflectionUtils.fixMethodAccess(method);
                           this.log.debug("Setting property " + propertyName);

                           try {
                              method.invoke(zoneProvider, value);
                           } catch (IllegalArgumentException e) {
                              this.log.error("Unable to set property " + propertyName + " on zone provider " + name + " (" + className + ")", e);
                              System.out.println("Unable to set property " + propertyName + " on zone provider " + name + " (" + className + ")");
                           } catch (InvocationTargetException e) {
                              this.log.error("Unable to set property " + propertyName + " on zone provider " + name + " (" + className + ")", e);
                              System.out.println("Unable to set property " + propertyName + " on zone provider " + name + " (" + className + ")");
                           }
                        } catch (SecurityException e) {
                           this.log.error("Unable to find matching setter method for property " + propertyName + " in zone provider " + name + " (" + className + ")", e);
                           System.out.println("Unable to find matching setter method for property " + propertyName + " in zone provider " + name + " (" + className + ")");
                        } catch (NoSuchMethodException e) {
                           this.log.error("Unable to find matching setter method for property " + propertyName + " in zone provider " + name + " (" + className + ")", e);
                           System.out.println("Unable to find matching setter method for property " + propertyName + " in zone provider " + name + " (" + className + ")");
                        }
                     }
                  }

                  try {
                     if (zoneProvider instanceof ZoneProviderUpdatable) {
                        ((ZoneProviderUpdatable)zoneProvider).setChangeListener(new ZoneChangeCallback() {
                           public void zoneDataChanged() {
                              EagleDNS.this.reloadZones();
                           }
                        });
                     }

                     zoneProvider.init(name);
                     this.log.info("Zone provider " + name + " (" + className + ") successfully initialized!");
                     System.out.println("Zone provider " + name + " (" + className + ") successfully initialized!");
                     this.zoneProviders.put(name, zoneProvider);
                  } catch (Throwable e) {
                     this.log.error("Error initializing zone provider " + name + " (" + className + ")", e);
                     System.out.println("Error initializing zone provider " + name + " (" + className + ")");
                  }
               } catch (InstantiationException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (IllegalAccessException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (ClassNotFoundException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (IllegalArgumentException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (InvocationTargetException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (NoSuchMethodException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               } catch (SecurityException e) {
                  this.log.error("Unable to create instance of class " + className + " for zone provider " + name, e);
                  System.out.println("Unable to create instance of class " + className + " for zone provider " + name);
               }
            }
         }
      }

      if (requireZones && this.zoneProviders.isEmpty()) {
         this.log.fatal("No started zone providers found, aborting startup! (disable the /Config/System/RequireZones property in configuration file if you wish to proceed without any zones or zone provider)");
         System.out.println("No started zone providers found, aborting startup! (disable the /Config/System/RequireZones property in configuration file if you wish to proceed without any zones or zone provider)");
      } else {
         this.reloadZones();
         if (requireZones && this.primaryZoneMap.isEmpty() && this.secondaryZoneMap.isEmpty()) {
            this.log.fatal("No zones found, aborting startup! (disable the /Config/System/RequireZones property in configuration file if you wish to proceed without any zones or zone provider)");
            System.out.println("No zones found, aborting startup! (disable the /Config/System/RequireZones property in configuration file if you wish to proceed without any zones or zone provider)");
         } else {
            for(SettingNode resolverElement : configFile.getNodes("/Config/Resolvers/Resolver")) {
               String name = resolverElement.getString("Name");
               if (StringUtils.isEmpty(name)) {
                  this.log.error("Resolver element with no name set found in config, ignoring element.");
                  System.out.println("Resolver element with no name set found in config, ignoring element.");
               } else {
                  String className = resolverElement.getString("Class");
                  if (StringUtils.isEmpty(className)) {
                     this.log.error("Resolver element " + name + " with no class set found in config, ignoring element.");
                     System.out.println("Resolver element " + name + " with no class set found in config, ignoring element.");
                  } else {
                     try {
                        this.log.debug("Instantiating resolver " + name + " (" + className + ")");
                        Resolver resolver = (Resolver)Class.forName(className).getDeclaredConstructor().newInstance();
                        this.log.debug("Resolver " + name + " successfully instantiated");

                        for(SettingNode propertyElement : resolverElement.getNodes("Properties/Property")) {
                           String propertyName = propertyElement.getString("@name");
                           if (StringUtils.isEmpty(propertyName)) {
                              this.log.error("Property element with no name set found in config for resolver " + name + ", ignoring element");
                              System.out.println("Property element with no name set found in config for resolver " + name + ", ignoring element");
                           } else {
                              String value = propertyElement.getString(".");
                              this.log.debug("Found value " + value + " for property " + propertyName);

                              try {
                                 Method method = resolver.getClass().getMethod("set" + StringUtils.toFirstLetterUppercase(propertyName), String.class);
                                 ReflectionUtils.fixMethodAccess(method);
                                 this.log.debug("Setting property " + propertyName);

                                 try {
                                    method.invoke(resolver, value);
                                 } catch (IllegalArgumentException e) {
                                    this.log.error("Unable to set property " + propertyName + " on resolver " + name + " (" + className + ")", e);
                                    System.out.println("Unable to set property " + propertyName + " on resolver " + name + " (" + className + ")");
                                 } catch (InvocationTargetException e) {
                                    this.log.error("Unable to set property " + propertyName + " on resolver " + name + " (" + className + ")", e);
                                    System.out.println("Unable to set property " + propertyName + " on resolver " + name + " (" + className + ")");
                                 }
                              } catch (SecurityException e) {
                                 this.log.error("Unable to find matching setter method for property " + propertyName + " in resolver " + name + " (" + className + ")", e);
                                 System.out.println("Unable to find matching setter method for property " + propertyName + " in resolver " + name + " (" + className + ")");
                              } catch (NoSuchMethodException e) {
                                 this.log.error("Unable to find matching setter method for property " + propertyName + " in resolver " + name + " (" + className + ")", e);
                                 System.out.println("Unable to find matching setter method for property " + propertyName + " in resolver " + name + " (" + className + ")");
                              }
                           }
                        }

                        try {
                           resolver.setSystemInterface(this);
                           resolver.init(name);
                           this.log.info("Resolver " + name + " (" + className + ") successfully initialized!");
                           System.out.println("Resolver " + name + " (" + className + ") successfully initialized!");
                           this.resolvers.add(new SimpleEntry(name, resolver));
                        } catch (Throwable e) {
                           this.log.error("Error initializing resolver " + name + " (" + className + ")", e);
                           System.out.println("Error initializing resolver " + name + " (" + className + ")");
                        }
                     } catch (InstantiationException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (IllegalAccessException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (ClassNotFoundException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (IllegalArgumentException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (InvocationTargetException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (NoSuchMethodException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     } catch (SecurityException e) {
                        this.log.error("Unable to create instance of class " + className + " for resolver " + name, e);
                        System.out.println("Unable to create instance of class " + className + " for resolver " + name + " " + e);
                     }
                  }
               }
            }

            if (this.resolvers.isEmpty()) {
               this.log.fatal("No started resolvers found, aborting startup!");
               System.out.println("No started resolvers found, aborting startup!");
            } else {
               for(SettingNode pluginElement : configFile.getNodes("/Config/Plugins/Plugin")) {
                  String name = pluginElement.getString("Name");
                  if (StringUtils.isEmpty(name)) {
                     this.log.error("Plugin element with no name set found in config, ignoring element.");
                     System.out.println("Plugin element with no name set found in config, ignoring element.");
                  } else {
                     String className = pluginElement.getString("Class");
                     if (StringUtils.isEmpty(className)) {
                        this.log.error("Plugin element " + name + " with no class set found in config, ignoring element.");
                        System.out.println("Plugin element " + name + " with no class set found in config, ignoring element.");
                     } else {
                        try {
                           this.log.debug("Instantiating plugin " + name + " (" + className + ")");
                           Plugin plugin = (Plugin)Class.forName(className).getDeclaredConstructor().newInstance();
                           this.log.debug("Plugin " + name + " successfully instantiated");

                           for(SettingNode propertyElement : pluginElement.getNodes("Properties/Property")) {
                              String propertyName = propertyElement.getString("@name");
                              if (StringUtils.isEmpty(propertyName)) {
                                 this.log.error("Property element with no name set found in config for plugin " + name + ", ignoring element");
                                 System.out.println("Property element with no name set found in config for plugin " + name + ", ignoring element");
                              } else {
                                 String value = propertyElement.getString(".");
                                 this.log.debug("Found value " + value + " for property " + propertyName);

                                 try {
                                    Method method = plugin.getClass().getMethod("set" + StringUtils.toFirstLetterUppercase(propertyName), String.class);
                                    ReflectionUtils.fixMethodAccess(method);
                                    this.log.debug("Setting property " + propertyName);

                                    try {
                                       method.invoke(plugin, value);
                                    } catch (IllegalArgumentException e) {
                                       this.log.error("Unable to set property " + propertyName + " on plugin " + name + " (" + className + ")", e);
                                       System.out.println("Unable to set property " + propertyName + " on plugin " + name + " (" + className + ")");
                                    } catch (InvocationTargetException e) {
                                       this.log.error("Unable to set property " + propertyName + " on plugin " + name + " (" + className + ")", e);
                                       System.out.println("Unable to set property " + propertyName + " on plugin " + name + " (" + className + ")");
                                    }
                                 } catch (SecurityException e) {
                                    this.log.error("Unable to find matching setter method for property " + propertyName + " in plugin " + name + " (" + className + ")", e);
                                    System.out.println("Unable to find matching setter method for property " + propertyName + " in plugin " + name + " (" + className + ")");
                                 } catch (NoSuchMethodException e) {
                                    this.log.error("Unable to find matching setter method for property " + propertyName + " in plugin " + name + " (" + className + ")", e);
                                    System.out.println("Unable to find matching setter method for property " + propertyName + " in plugin " + name + " (" + className + ")");
                                 }
                              }
                           }

                           try {
                              plugin.setSystemInterface(this);
                              plugin.init(name);
                              this.log.info("Plugin " + name + " (" + className + ") successfully initialized!");
                              System.out.println("Plugin " + name + " (" + className + ") successfully initialized!");
                              this.plugins.put(name, plugin);
                           } catch (Throwable e) {
                              this.log.error("Error initializing plugin " + name + " (" + className + ")", e);
                              System.out.println("Error initializing plugin " + name + " (" + className + ")");
                           }
                        } catch (InstantiationException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (IllegalAccessException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (ClassNotFoundException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (IllegalArgumentException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (InvocationTargetException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (NoSuchMethodException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        } catch (SecurityException e) {
                           this.log.error("Unable to create instance of class " + className + " for plugin " + name, e);
                           System.out.println("Unable to create instance of class " + className + " for plugin " + name);
                        }
                     }
                  }
               }

               this.log.info("Initializing TCP thread pool...");
               this.tcpThreadPool = new ThreadPoolExecutor(this.tcpThreadPoolMinSize, this.tcpThreadPoolMaxSize, 60L, TimeUnit.SECONDS, new SynchronousQueue(true));
               this.log.info("Initializing UDP thread pool...");
               this.udpThreadPool = new ThreadPoolExecutor(this.udpThreadPoolMinSize, this.udpThreadPoolMaxSize, 60L, TimeUnit.SECONDS, new SynchronousQueue(true));

               for(InetAddress addr : addresses) {
                  for(int port : ports) {
                     try {
                        this.udpMonitorThreads.add(new UDPSocketMonitor(this, addr, port));
                     } catch (SocketException e) {
                        this.log.error("Unable to open UDP server socket on address " + addr + ":" + port + ", " + e);
                        System.out.println("Unable to open UDP server socket on address " + addr + ":" + port + ", " + e);
                     }

                     try {
                        this.tcpMonitorThreads.add(new TCPSocketMonitor(this, addr, port));
                     } catch (IOException e) {
                        this.log.error("Unable to open TCP server socket on address " + addr + ":" + port + ", " + e);
                        System.out.println("Unable to open TCP server socket on address " + addr + ":" + port + ", " + e);
                     }
                  }
               }

               if (this.tcpMonitorThreads.isEmpty() && this.udpMonitorThreads.isEmpty()) {
                  this.log.fatal("Not bound on any sockets, aborting startup!");
                  System.out.println("Not bound on any sockets, aborting startup!");
               } else {
                  this.log.info("Starting secondary zone update timer...");
                  this.timerTask = new RunnableTimerTask(this);
                  this.secondaryZoneUpdateTimer = new Timer();
                  this.secondaryZoneUpdateTimer.schedule(this.timerTask, 60000L, 60000L);
                  this.log.fatal(VERSION + " started with " + this.primaryZoneMap.size() + " primary zones and " + this.secondaryZoneMap.size() + " secondary zones, " + this.zoneProviders.size() + " Zone providers and " + this.resolvers.size() + " resolvers");
                  System.out.println(VERSION + " started with " + this.primaryZoneMap.size() + " primary zones and " + this.secondaryZoneMap.size() + " secondary zones, " + this.zoneProviders.size() + " Zone providers and " + this.resolvers.size() + " resolvers");
                  this.status = Status.STARTED;
                  System.out.close();
                  System.err.close();
               }
            }
         }
      }
   }

   public synchronized void shutdown() {
      if (this.status == Status.STARTING || this.status == Status.STARTED) {
         this.log.fatal("Shutting down " + VERSION + "...");
         this.status = Status.SHUTTING_DOWN;
         this.log.info("Stopping secondary zone update timer...");
         this.timerTask.cancel();
         this.secondaryZoneUpdateTimer.cancel();
         this.log.info("Stopping TCP thread pool...");
         this.tcpThreadPool.shutdown();

         try {
            this.tcpThreadPool.awaitTermination((long)this.tcpThreadPoolShutdownTimeout, TimeUnit.SECONDS);
         } catch (InterruptedException var6) {
            this.log.error("Timeout waiting " + this.tcpThreadPoolShutdownTimeout + " seconds for TCP thread pool to shutdown, forcing thread pool shutdown...");
            this.tcpThreadPool.shutdownNow();
         }

         this.log.info("Stopping UDP thread pool...");
         this.udpThreadPool.shutdown();

         try {
            this.udpThreadPool.awaitTermination((long)this.udpThreadPoolShutdownTimeout, TimeUnit.SECONDS);
         } catch (InterruptedException var5) {
            this.log.error("Timeout waiting " + this.udpThreadPoolShutdownTimeout + " seconds for UDP thread pool to shutdown, forcing thread pool shutdown...");
            this.udpThreadPool.shutdownNow();
         }

         Iterator<Map.Entry<String, Plugin>> pluginIterator = this.plugins.entrySet().iterator();

         while(pluginIterator.hasNext()) {
            Map.Entry<String, Plugin> pluginEntry = (Map.Entry)pluginIterator.next();
            this.stopPlugin(pluginEntry, "plugin");
            pluginIterator.remove();
         }

         Iterator<Map.Entry<String, Resolver>> resolverIterator = this.resolvers.iterator();

         while(resolverIterator.hasNext()) {
            Map.Entry<String, Resolver> resolverEntry = (Map.Entry)resolverIterator.next();
            this.stopPlugin(resolverEntry, "resolver");
            resolverIterator.remove();
         }

         Iterator<Map.Entry<String, ZoneProvider>> zoneProviderIterator = this.zoneProviders.entrySet().iterator();

         while(zoneProviderIterator.hasNext()) {
            Map.Entry<String, ZoneProvider> zoneProviderEntry = (Map.Entry)zoneProviderIterator.next();
            this.stopPlugin(zoneProviderEntry, "zone provider");
            zoneProviderIterator.remove();
         }

         this.log.fatal(VERSION + " stopped");
         System.exit(0);
      }

   }

   private void stopPlugin(Map.Entry pluginEntry, String type) {
      this.log.debug("Shutting down " + type + " " + (String)pluginEntry.getKey() + "...");

      try {
         ((Plugin)pluginEntry.getValue()).shutdown();
         this.log.info(type + " " + (String)pluginEntry.getKey() + " shutdown");
      } catch (Throwable t) {
         this.log.error("Error shutting down " + type + " " + (String)pluginEntry.getKey(), t);
      }

   }

   public synchronized void reloadZones() {
      ConcurrentHashMap<Name, CachedPrimaryZone> primaryZoneMap = new ConcurrentHashMap();
      ConcurrentHashMap<Name, CachedSecondaryZone> secondaryZoneMap = new ConcurrentHashMap();

      for(Map.Entry zoneProviderEntry : this.zoneProviders.entrySet()) {
         this.log.info("Getting primary zones from zone provider " + (String)zoneProviderEntry.getKey());

         Collection<Zone> primaryZones;
         try {
            primaryZones = ((ZoneProvider)zoneProviderEntry.getValue()).getPrimaryZones();
         } catch (Throwable e) {
            this.log.error("Error getting primary zones from zone provider " + (String)zoneProviderEntry.getKey(), e);
            continue;
         }

         if (primaryZones != null) {
            for(Zone zone : primaryZones) {
               this.log.info("Got zone " + zone.getOrigin());
               primaryZoneMap.put(zone.getOrigin(), new CachedPrimaryZone(zone, (ZoneProvider)zoneProviderEntry.getValue()));
            }
         }

         this.log.info("Getting secondary zones from zone provider " + (String)zoneProviderEntry.getKey());

         Collection<SecondaryZone> secondaryZones;
         try {
            secondaryZones = ((ZoneProvider)zoneProviderEntry.getValue()).getSecondaryZones();
         } catch (Throwable e) {
            this.log.error("Error getting secondary zones from zone provider " + (String)zoneProviderEntry.getKey(), e);
            continue;
         }

         if (secondaryZones != null) {
            for(SecondaryZone zone : secondaryZones) {
               this.log.info("Got zone " + zone.getZoneName() + " (" + zone.getRemoteServerAddress() + ")");
               CachedSecondaryZone cachedSecondaryZone = new CachedSecondaryZone((ZoneProvider)zoneProviderEntry.getValue(), zone);
               secondaryZoneMap.put(cachedSecondaryZone.getSecondaryZone().getZoneName(), cachedSecondaryZone);
            }
         }
      }

      this.primaryZoneMap = primaryZoneMap;
      this.secondaryZoneMap = secondaryZoneMap;
   }

   private void addTSIG(String algstr, String namestr, String key) throws IOException {
      Name name = Name.fromString(namestr, Name.root);
      this.TSIGs.put(name, new TSIG(algstr, namestr, key));
   }

   public Zone getZone(Name name) {
      CachedPrimaryZone cachedPrimaryZone = (CachedPrimaryZone)this.primaryZoneMap.get(name);
      if (cachedPrimaryZone != null) {
         return cachedPrimaryZone.getZone();
      } else {
         CachedSecondaryZone cachedSecondaryZone = (CachedSecondaryZone)this.secondaryZoneMap.get(name);
         return cachedSecondaryZone != null && cachedSecondaryZone.getSecondaryZone().getZoneCopy() != null ? cachedSecondaryZone.getSecondaryZone().getZoneCopy() : null;
      }
   }

   byte[] generateReply(Message query, byte[] in, int length, Socket socket, SocketAddress socketAddress) throws IOException {
      if (this.log.isDebugEnabled()) {
         this.log.debug("Processing query " + toString(query.getQuestion()) + " from " + socketAddress);
         this.log.debug("Full query:\n" + query);
      }

      Message response = null;
      Request request = new DefaultRequest(socketAddress, query, in, length, socket);

      for(Map.Entry resolverEntry : this.resolvers) {
         try {
            response = ((Resolver)resolverEntry.getValue()).generateReply(request);
            if (response != null) {
               if (this.log.isDebugEnabled()) {
                  this.log.debug("Resolver " + (String)resolverEntry.getKey() + " responded to query " + toString(query.getQuestion()) + " with response " + Rcode.string(response.getHeader().getRcode()) + " containing " + response.getSection(1).size() + " answer, " + response.getSection(2).size() + " authoritative and " + response.getSection(3).size() + " additional records");
                  this.log.debug(response);
               }
               break;
            }

            if (socket != null && socket.isClosed()) {
               this.log.info("TCP response sent by resolver " + (String)resolverEntry.getKey() + " for query " + toString(query.getQuestion()));
               return null;
            }
         } catch (Exception e) {
            this.log.error("Caught exception from resolver " + (String)resolverEntry.getKey(), e);
         }
      }

      if (socket != null && socket.isClosed()) {
         return null;
      } else {
         OPTRecord queryOPT = query.getOPT();
         if (response == null) {
            response = this.getInternalResponse(query, in, length, socket, queryOPT);
            this.log.info("Got no response from resolvers for query " + toString(query.getQuestion()) + " sending default response " + Rcode.string(this.defaultResponse));
         }

         int maxLength;
         if (socket != null) {
            maxLength = 65535;
         } else if (queryOPT != null) {
            maxLength = Math.max(queryOPT.getPayloadSize(), 512);
         } else {
            maxLength = 512;
         }

         return response.toWire(maxLength);
      }
   }

   private Message getInternalResponse(Message query, byte[] in, int length, Socket socket, OPTRecord queryOPT) {
      int flags = 0;
      Header header = query.getHeader();
      if (header.getFlag(0)) {
         return null;
      } else if (header.getRcode() != 0) {
         return errorMessage(query, 1);
      } else if (header.getOpcode() != 0) {
         return errorMessage(query, 4);
      } else {
         TSIGRecord queryTSIG = query.getTSIG();
         TSIG tsig = null;
         if (queryTSIG != null) {
            tsig = (TSIG)this.TSIGs.get(queryTSIG.getName());
            if (tsig == null || tsig.verify(query, in, (TSIGRecord)null) != 0) {
               return this.formerrMessage(in);
            }
         }

         if (queryOPT != null && (queryOPT.getFlags() & '耀') != 0) {
            flags = 1;
         }

         Message response = new Message(query.getHeader().getID());
         response.getHeader().setFlag(0);
         if (query.getHeader().getFlag(7)) {
            response.getHeader().setFlag(7);
         }

         response.getHeader().setRcode(this.defaultResponse);
         Record queryRecord = query.getQuestion();
         response.addRecord(queryRecord, 0);
         int type = queryRecord.getType();
         if (type == 252 && socket != null) {
            return errorMessage(query, 5);
         } else if (!Type.isRR(type) && type != 255) {
            return errorMessage(query, 4);
         } else {
            if (queryOPT != null) {
               int optflags = flags == 1 ? '耀' : 0;
               OPTRecord opt = new OPTRecord(4096, this.defaultResponse, 0, optflags);
               response.addRecord(opt, 3);
            }

            response.setTSIG(tsig, this.defaultResponse, queryTSIG);
            return response;
         }
      }
   }

   public static Message buildErrorMessage(Header header, int rcode, Record question) {
      Message response = new Message();
      response.setHeader(header);

      for(int i = 0; i < 4; ++i) {
         response.removeAllRecords(i);
      }

      if (rcode == 2) {
         response.addRecord(question, 0);
      }

      header.setRcode(rcode);
      return response;
   }

   Message formerrMessage(byte[] in) {
      Header header;
      try {
         header = new Header(in);
      } catch (IOException var4) {
         return null;
      }

      return buildErrorMessage(header, 1, (Record)null);
   }

   public static Message errorMessage(Message query, int rcode) {
      return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
   }

   protected void UDPClient(DatagramSocket socket, DatagramPacket inDataPacket) {
   }

   public static String toString(Record record) {
      if (record == null) {
         return null;
      } else {
         StringBuilder stringBuilder = new StringBuilder();
         stringBuilder.append(record.getName());
         stringBuilder.append(" ");
         stringBuilder.append(record.getTTL());
         stringBuilder.append(" ");
         stringBuilder.append(DClass.string(record.getDClass()));
         stringBuilder.append(" ");
         stringBuilder.append(Type.string(record.getType()));
         String rdata = record.rdataToString();
         if (!rdata.equals("")) {
            stringBuilder.append(" ");
            stringBuilder.append(rdata);
         }

         return stringBuilder.toString();
      }
   }

   public static void main(String[] args) {
      if (args.length > 1) {
         System.out.println("usage: EagleDNS [conf]");
         System.exit(0);
      }

      try {
         String configFilePath;
         if (args.length == 1) {
            configFilePath = args[0];
         } else {
            configFilePath = "conf/config.xml";
         }

         new EagleDNS(configFilePath);
      } catch (IOException e) {
         System.out.println(e);
      }

   }

   public void run() {
      this.log.debug("Checking secondary zones...");

      for(CachedSecondaryZone cachedSecondaryZone : this.secondaryZoneMap.values()) {
         SecondaryZone secondaryZone = cachedSecondaryZone.getSecondaryZone();
         if (secondaryZone.getZoneCopy() == null || secondaryZone.getDownloaded() == null || System.currentTimeMillis() - secondaryZone.getDownloaded().getTime() > secondaryZone.getZoneCopy().getSOA().getRefresh() * 1000L) {
            cachedSecondaryZone.update(this.axfrTimeout);
         }
      }

   }

   protected ThreadPoolExecutor getTcpThreadPool() {
      return this.tcpThreadPool;
   }

   protected ThreadPoolExecutor getUdpThreadPool() {
      return this.udpThreadPool;
   }

   public Status getStatus() {
      return this.status;
   }

   public TSIG getTSIG(Name name) {
      return (TSIG)this.TSIGs.get(name);
   }

   public int primaryZoneCount() {
      return this.primaryZoneMap.size();
   }

   public int secondaryZoneCount() {
      return this.secondaryZoneMap.size();
   }

   public int getResolverCount() {
      return this.resolvers.size();
   }

   public int getActiveTCPThreadCount() {
      return this.tcpThreadPool.getActiveCount();
   }

   public int getTCPThreadPoolMaxSize() {
      return this.tcpThreadPool.getMaximumPoolSize();
   }

   public long getCompletedTCPQueryCount() {
      return this.tcpThreadPool.getCompletedTaskCount();
   }

   public int getMaxActiveTCPThreadCount() {
      return this.tcpThreadPool.getLargestPoolSize();
   }

   public int getActiveUDPThreadCount() {
      return this.udpThreadPool.getActiveCount();
   }

   public int getUDPThreadPoolMaxSize() {
      return this.udpThreadPool.getMaximumPoolSize();
   }

   public long getCompletedUDPQueryCount() {
      return this.udpThreadPool.getCompletedTaskCount();
   }

   public int getMaxActiveUDPThreadCount() {
      return this.udpThreadPool.getLargestPoolSize();
   }

   public long getStartTime() {
      return this.startTime;
   }

   public String getVersion() {
      return VERSION;
   }

   public Plugin getPlugin(String name) {
      return (Plugin)this.plugins.get(name);
   }

   public Set getPlugins() {
      return this.plugins.entrySet();
   }

   public Resolver getResolver(String name) {
      for(Map.Entry resolverEntry : this.resolvers) {
         if (((String)resolverEntry.getKey()).equals(name)) {
            return (Resolver)resolverEntry.getValue();
         }
      }

      return null;
   }

   public List getResolvers() {
      return this.resolvers;
   }

   public ZoneProvider getZoneProvider(String name) {
      return (ZoneProvider)this.zoneProviders.get(name);
   }

   public Set getZoneProviders() {
      return this.zoneProviders.entrySet();
   }

   public int getUDPThreadPoolMinSize() {
      return this.udpThreadPool.getCorePoolSize();
   }

   public int getTCPThreadPoolMinSize() {
      return this.tcpThreadPool.getCorePoolSize();
   }

   protected void incrementRejectedTCPConnections() {
      this.rejectedTCPConnections.increment();
   }

   protected void incrementRejectedUDPConnections() {
      this.rejectedUDPConnections.increment();
   }

   public long getRejectedTCPConnections() {
      return this.rejectedTCPConnections.getValue();
   }

   public long getRejectedUDPConnections() {
      return this.rejectedUDPConnections.getValue();
   }
}
