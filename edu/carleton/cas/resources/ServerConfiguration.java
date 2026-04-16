package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.file.Utils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Random;

public final class ServerConfiguration {
   private final int DEFAULT_PORT;
   private final int DEFAULT_TIMEOUT;
   private String specification;
   private Server[] servers;
   private Random random;
   private boolean canReuse;
   private Server serverInUse;

   public ServerConfiguration(String specification) {
      this(specification, false);
   }

   public ServerConfiguration(String specification, boolean canReuse) {
      this.DEFAULT_PORT = Integer.parseInt("8443");
      this.DEFAULT_TIMEOUT = ClientShared.CONNECTION_TIMEOUT_IN_MSECS;
      this.specification = specification;
      this.random = new Random();
      this.canReuse = canReuse;
      this.serverInUse = null;
   }

   public String getServerInUse() {
      return this.serverInUse == null ? null : this.serverInUse.getName();
   }

   public void setServerInUse(String _server) {
      Server[] var5;
      for(Server server : var5 = this.servers) {
         if (server.getName().equals(_server)) {
            if (this.isOkToUse(_server, false)) {
               this.serverInUse = server;
            }

            return;
         }
      }

   }

   public void canReuse() {
      this.canReuse = true;
   }

   public void process(String specification) {
      this.specification = specification;
      this.process();
   }

   public void process() {
      if (this.specification == null) {
         this.servers = null;
      } else {
         String[] hosts = this.specification.split(",");
         if (hosts != null) {
            this.servers = new Server[hosts.length];

            for(int i = 0; i < hosts.length; ++i) {
               this.servers[i] = new Server(hosts[i].trim());
            }

            this.normalize();
         } else {
            this.servers = null;
         }

      }
   }

   private void normalize() {
      int total_percentage = 0;
      int number_of_servers_with_zero_percentage = 0;

      Server[] var7;
      for(Server server : var7 = this.servers) {
         int server_percentage = server.getPercentage();
         if (server_percentage == 0) {
            ++number_of_servers_with_zero_percentage;
         } else {
            total_percentage += server_percentage;
         }
      }

      int residual = 100 - total_percentage;
      if (residual != 0) {
         if (residual < 0) {
            float multiplier = 100.0F / (float)total_percentage;

            Server[] var9;
            for(Server server : var9 = this.servers) {
               int server_percentage = server.getPercentage();
               if (server_percentage > 0) {
                  server_percentage = Math.round((float)server_percentage * multiplier);
                  server.setPercentage(server_percentage);
               }
            }
         } else if (number_of_servers_with_zero_percentage > 0) {
            int increment = residual / number_of_servers_with_zero_percentage;

            Server[] var24;
            for(Server server : var24 = this.servers) {
               if (server.getPercentage() == 0) {
                  server.setPercentage(increment);
               }
            }
         }

         Server[] var23;
         for(Server server : var23 = this.servers) {
            if (server.getPercentage() == 0) {
               server.beUsed();
            }
         }

      }
   }

   public void reset() {
      Server[] var4;
      for(Server server : var4 = this.servers) {
         server.reset();
      }

   }

   public boolean reset(String name) {
      Server[] var5;
      for(Server server : var5 = this.servers) {
         if (server.getName().equals(name)) {
            server.reset();
            return true;
         }
      }

      return false;
   }

   public String next(boolean used) {
      String server = this.next(used, this.canReuse);
      if (server == null && this.canReuse && this.nextIsOkToUse()) {
         server = this.next(used, false);
      }

      return server;
   }

   public String next(boolean used, boolean canReuseAndTryAgain) {
      if (this.servers == null) {
         return null;
      } else {
         Server last_server = new Server();

         Server[] var7;
         for(Server server : var7 = this.servers) {
            server.setCumulative(last_server);
            last_server = server;
         }

         if (last_server.getCumulative() == 0) {
            if (canReuseAndTryAgain) {
               this.reset();
            }

            return null;
         } else {
            int r = this.random.nextInt(last_server.getCumulative() + 1);

            Server[] var8;
            for(Server server : var8 = this.servers) {
               if (!server.isUsed() && r <= server.getCumulative()) {
                  if (used) {
                     server.beUsed();
                  }

                  return server.getName();
               }
            }

            if (canReuseAndTryAgain) {
               this.reset();
            }

            return null;
         }
      }
   }

   public boolean nextIsOkToUse(boolean testForReachability) {
      String next = this.next(false, false);
      return this.isOkToUse(next, testForReachability);
   }

   public boolean nextIsOkToUse() {
      return this.nextIsOkToUse(false);
   }

   public boolean isOkToUse(String server, boolean testForReachability) {
      if (server == null) {
         return false;
      } else {
         String address = null;

         try {
            String[] tokens = server.split(":");
            int port;
            if (tokens.length == 2) {
               address = tokens[0].trim();
               port = Integer.parseInt(tokens[1].trim());
               if (port < 0 || port >= 65536) {
                  return false;
               }
            } else {
               address = server.trim();
               port = this.DEFAULT_PORT;
            }

            if (testForReachability && !Utils.isReachable(address, port, this.DEFAULT_TIMEOUT)) {
               return false;
            } else {
               InetAddress.getByName(address);
               return true;
            }
         } catch (NumberFormatException | UnknownHostException var6) {
            return false;
         }
      }
   }

   public String toString() {
      StringBuffer buff = new StringBuffer("ServerConfiguration[ ");
      if (this.servers != null) {
         Server[] var5;
         for(Server server : var5 = this.servers) {
            buff.append(server);
            buff.append(" ");
         }
      }

      buff.append("]");
      return buff.toString();
   }

   public static void main(String[] args) {
      boolean output = false;
      int no_tests = 1000;
      HashMap<String, Integer> freq = new HashMap();
      runTest(output, true, 1, no_tests, freq, "a:9999, b, c, d ");
      runTest(output, false, 2, no_tests, freq, "a;40, b, c ");
      runTest(output, false, 3, no_tests, freq, "a ; 40, b:4444 ; 80, c ");
      runTest(output, true, 4, no_tests, freq, "a ; 40, b ; 80, c ");
      runTest(output, true, 5, no_tests, freq, "a");
      runTest(output, false, 6, no_tests, freq, "a");
      runTest(output, false, 7, no_tests, freq, "a,b");
      runTest(output, false, 8, no_tests, freq, "a;69,b;31");
      runTest(output, false, 9, no_tests, freq, "b;31,a;69");
      runTest(output, false, 10, no_tests, freq, "a;1000,b;-1");
      runTest(output, false, 11, no_tests, freq, " ");
      runTest(output, false, 12, no_tests, freq, (String)null);
   }

   public static void runTest(boolean output, boolean used, int no, int no_tests, HashMap freq, String description) {
      freq.clear();
      ServerConfiguration sc = new ServerConfiguration(description);
      sc.process();
      System.out.println(description + " -> " + String.valueOf(sc));

      for(int i = 0; i < no_tests; ++i) {
         String s = sc.next(used);
         if (s != null) {
            Integer f = (Integer)freq.get(s);
            if (f == null) {
               freq.put(s, 1);
            } else {
               freq.put(s, f + 1);
            }
         }

         if (output) {
            System.out.println("Test " + no + " Server is: " + s);
         }
      }

      System.out.println("Test " + no + " freq: " + String.valueOf(freq));
   }

   public final class Server {
      private String name = "";
      private int percentage = 0;
      private boolean used = false;
      private int cumulative = 0;

      public Server() {
      }

      public Server(String specification) {
         this.process(specification);
      }

      private void process(String specification) {
         if (specification == null) {
            this.beUsed();
         } else {
            String[] host_percentage = specification.split(";");
            if (host_percentage != null) {
               if (host_percentage.length >= 2) {
                  this.name = host_percentage[0].trim();
                  if (this.name.length() == 0) {
                     this.beUsed();
                  }

                  try {
                     this.percentage = Integer.parseInt(host_percentage[1].trim());
                     if (this.percentage < 0 || this.percentage > 100) {
                        throw new NumberFormatException("Illegal server configuration percentage: " + host_percentage[1]);
                     }
                  } catch (NumberFormatException e) {
                     String var10001 = this.name;
                     System.err.println(var10001 + " " + String.valueOf(e));
                     this.percentage = 0;
                  }
               } else if (host_percentage.length == 1) {
                  this.name = host_percentage[0].trim();
                  if (this.name.length() == 0) {
                     this.beUsed();
                  }

                  this.percentage = 0;
               }
            }

         }
      }

      public String getName() {
         return this.name;
      }

      public int getPercentage() {
         return this.percentage;
      }

      public void setPercentage(int percentage) {
         if (percentage >= 0 && percentage <= 100) {
            this.percentage = percentage;
         }

      }

      public int getCumulative() {
         return this.cumulative;
      }

      public void setCumulative(Server server) {
         if (this.isUsed()) {
            this.cumulative = server.getCumulative();
         } else {
            this.cumulative = this.percentage + server.getCumulative();
         }

      }

      public boolean isUsed() {
         return this.used;
      }

      public void beUsed() {
         this.used = true;
      }

      public void reset() {
         this.used = false;
      }

      public String toString() {
         String var10000 = this.getName();
         return var10000 + "(" + this.getPercentage() + "," + this.isUsed() + ")";
      }
   }
}
