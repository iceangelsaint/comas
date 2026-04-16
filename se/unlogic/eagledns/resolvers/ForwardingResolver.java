package se.unlogic.eagledns.resolvers;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Timer;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.SimpleResolver;
import se.unlogic.eagledns.EagleDNS;
import se.unlogic.eagledns.Request;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.time.TimeUtils;
import se.unlogic.standardutils.timer.RunnableTimerTask;

public class ForwardingResolver extends BasePlugin implements Resolver, Runnable {
   protected String server;
   protected int port = 53;
   protected boolean tcp;
   protected Integer timeout;
   protected Integer maxerrors;
   protected Integer errorWindowsSize;
   protected String validationQuery = "google.com";
   protected int validationInterval = 5;
   protected SimpleResolver resolver;
   protected boolean online = true;
   private LinkedList errors = null;
   protected Lookup lookup;
   protected long requestsHandled;
   protected long requestsTimedout;
   protected String failoverResolverName;
   protected boolean replyOnTimeout = false;
   protected boolean replyOnUnsuccessfulLookup = false;
   protected Timer timer;

   public void init(String name) throws Exception {
      super.init(name);
      if (this.server == null) {
         throw new RuntimeException("No server set!");
      } else {
         this.resolver = new SimpleResolver(this.server);
         this.resolver.setPort(this.port);
         if (this.timeout != null) {
            this.resolver.setTimeout(Duration.ofSeconds((long)this.timeout));
         }

         this.log.info("Resolver " + name + " configured to forward queries to server " + this.server + ":" + this.port + " with timeout " + this.timeout + " sec.");
         if (this.failoverResolverName != null) {
            this.log.info("Resolver " + name + " configured to act as failover for resolver " + this.failoverResolverName + " and will therefore only handle queries when resolver " + this.failoverResolverName + " is offline");
         }

         if (this.maxerrors != null && this.errorWindowsSize != null) {
            this.log.info("Resolver " + name + " has maxerrors set to " + this.maxerrors + " and errorWindowsSize set to " + this.errorWindowsSize + ", enabling failover detection");
            this.errors = new LinkedList();
            this.lookup = new Lookup(this.validationQuery);
            this.lookup.setCache((Cache)null);
            this.lookup.setResolver(this.resolver);
            this.lookup.setSearchPath((String[])null);
            this.timer = new Timer(name, true);
            this.timer.scheduleAtFixedRate(new RunnableTimerTask(this), 0L, (long)(this.validationInterval * 1000));
            this.log.info("Status monitoring thread for resolver " + name + " started");
         }

      }
   }

   public Message generateReply(Request request) {
      if (this.failoverResolverName != null) {
         Resolver resolver = this.systemInterface.getResolver(this.failoverResolverName);
         if (resolver == null) {
            this.log.warn("Resolver " + this.name + " is configured to as failover for resolver " + this.failoverResolverName + " which cannot be found, ingnoring query " + EagleDNS.toString(request.getQuery().getQuestion()));
            return null;
         }

         if (!(resolver instanceof ForwardingResolver)) {
            this.log.warn("Resolver " + this.name + " is configured to as failover for resolver " + this.failoverResolverName + " which is not an instance of " + ForwardingResolver.class.getSimpleName() + ", ingnoring query " + EagleDNS.toString(request.getQuery().getQuestion()));
            return null;
         }

         if (((ForwardingResolver)resolver).online) {
            this.log.debug("Resolver " + this.name + " ignoring query " + EagleDNS.toString(request.getQuery().getQuestion()) + " since resolver " + this.failoverResolverName + " is online");
            return null;
         }
      }

      if (this.online) {
         try {
            this.log.debug("Resolver " + this.name + " forwarding query " + EagleDNS.toString(request.getQuery().getQuestion()) + " to server " + this.server + ":" + this.port);
            Message response = this.resolver.send(request.getQuery());
            this.log.debug("Resolver " + this.name + " got response " + Rcode.string(response.getHeader().getRcode()) + " with " + response.getSection(1).size() + " answer, " + response.getSection(2).size() + " authoritative and " + response.getSection(3).size() + " additional records");
            Integer rcode = response.getHeader().getRcode();
            if (this.replyOnUnsuccessfulLookup || rcode != null && rcode != 3 && rcode != 2 && (rcode != 0 || response.getSection(1).size() != 0 || response.getSection(2).size() != 0)) {
               ++this.requestsHandled;
               return response;
            }

            return null;
         } catch (SocketTimeoutException var4) {
            ++this.requestsTimedout;
            this.log.info("Timeout in resolver " + this.name + " while forwarding query " + EagleDNS.toString(request.getQuery().getQuestion()));
            if (this.replyOnTimeout) {
               return EagleDNS.errorMessage(request.getQuery(), 2);
            }
         } catch (IOException e) {
            this.log.warn("Error " + e + " in resolver " + this.name + " while forwarding query " + EagleDNS.toString(request.getQuery().getQuestion()));
         } catch (RuntimeException e) {
            this.log.warn("Error " + e + " in resolver " + this.name + " while forwarding query " + EagleDNS.toString(request.getQuery().getQuestion()));
         }
      } else {
         this.log.debug("Resolver " + this.name + " is offline skipping query " + EagleDNS.toString(request.getQuery().getQuestion()));
      }

      return null;
   }

   public synchronized void processError() {
      long currentTime = System.currentTimeMillis();
      this.errors.add(currentTime);
      if (this.errors.size() > this.maxerrors) {
         this.errors.removeFirst();
         if (this.online && (Long)this.errors.getFirst() > currentTime - (long)(1000 * this.errorWindowsSize)) {
            this.log.warn("Marking resolver " + this.name + " as offline after receiving " + this.maxerrors + " errors in " + TimeUtils.millisecondsToString(currentTime - (Long)this.errors.getFirst()));
            this.online = false;
         }
      }

   }

   public void setServer(String server) {
      this.server = server;
   }

   public void setTimeout(String stringTimeout) {
      if (stringTimeout != null) {
         Integer timeout = NumberUtils.toInt(stringTimeout);
         if (timeout != null && timeout >= 1) {
            this.timeout = timeout;
         } else {
            this.log.warn("Invalid timeout " + stringTimeout + " specified");
         }
      } else {
         this.timeout = null;
      }

   }

   public void setMaxerrors(String maxerrorsString) {
      if (maxerrorsString != null) {
         Integer maxerrors = NumberUtils.toInt(maxerrorsString);
         if (maxerrors != null && maxerrors >= 1) {
            this.maxerrors = maxerrors;
         } else {
            this.log.warn("Invalid max error value " + maxerrorsString + " specified");
         }
      } else {
         this.maxerrors = null;
      }

   }

   public void setErrorWindowsSize(String errorWindowsSizeString) {
      if (errorWindowsSizeString != null) {
         Integer errorWindowsSize = NumberUtils.toInt(errorWindowsSizeString);
         if (errorWindowsSize != null && errorWindowsSize >= 1) {
            this.errorWindowsSize = errorWindowsSize;
         } else {
            this.log.warn("Invalid error window size " + errorWindowsSizeString + " specified");
         }
      } else {
         this.errorWindowsSize = null;
      }

   }

   public void setPort(String portString) {
      Integer port = NumberUtils.toInt(portString);
      if (port != null && port >= 1 && port <= 65536) {
         this.port = port;
      } else {
         this.log.warn("Invalid port " + portString + " specified! (sticking to default value " + this.port + ")");
      }

   }

   public void setTcp(String tcp) {
      this.tcp = Boolean.parseBoolean(tcp);
   }

   public void setValidationQuery(String validationQuery) {
      this.validationQuery = validationQuery;
   }

   public void setValidationInterval(String validationIntervalString) {
      Integer validationInterval = NumberUtils.toInt(validationIntervalString);
      if (validationInterval != null && validationInterval > 0) {
         this.validationInterval = validationInterval;
      } else {
         this.log.warn("Invalid validation interval " + validationIntervalString + " specified!");
      }

   }

   public void run() {
      try {
         this.lookup.run();
         if (this.online) {
            if (this.lookup.getResult() == 0) {
               this.log.debug("Resolver " + this.name + " is still up, got response " + Rcode.string(this.lookup.getResult()) + " from upstream server for query " + this.validationQuery);
            } else {
               this.log.warn("Resolver " + this.name + " got unsuccessful response " + Rcode.string(this.lookup.getResult()) + " from upstream server for query " + this.validationQuery);
               this.processError();
            }
         } else if (this.lookup.getResult() == 0) {
            this.log.warn("Marking resolver " + this.name + " as online after getting succesful response from query for " + this.validationQuery);
            this.online = true;
         } else {
            this.log.debug("Resolver " + this.name + " is still down, got response " + Rcode.string(this.lookup.getResult()) + " from upstream server for query " + this.validationQuery);
         }
      } catch (RuntimeException e) {
         if (this.online) {
            this.log.warn("Marking resolver " + this.name + " as offline after getting error " + e + " when trying to resolve query " + this.validationQuery);
            this.online = false;
         } else {
            this.log.debug("Resolver " + this.name + " is still down, got error " + e + " when trying to resolve " + this.validationQuery);
         }
      }

   }

   public long getRequestsHandled() {
      return this.requestsHandled;
   }

   public long getRequestsTimedout() {
      return this.requestsTimedout;
   }

   public void setFailoverForResolver(String resolverName) {
      this.failoverResolverName = resolverName;
   }

   public void setReplyOnTimeout(String replyOnTimeout) {
      this.replyOnTimeout = Boolean.parseBoolean(replyOnTimeout);
   }

   public void setReplyOnUnsuccessfulLookup(String replyOnUnsuccessfulLookup) {
      this.replyOnUnsuccessfulLookup = Boolean.parseBoolean(replyOnUnsuccessfulLookup);
   }

   public void shutdown() throws Exception {
      if (this.timer != null) {
         this.timer.cancel();
      }

      super.shutdown();
   }
}
