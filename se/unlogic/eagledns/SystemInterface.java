package se.unlogic.eagledns;

import java.util.List;
import java.util.Set;
import org.xbill.DNS.Name;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.Zone;
import se.unlogic.eagledns.plugins.Plugin;
import se.unlogic.eagledns.resolvers.Resolver;
import se.unlogic.eagledns.zoneproviders.ZoneProvider;

public interface SystemInterface {
   Zone getZone(Name var1);

   TSIG getTSIG(Name var1);

   void shutdown();

   void reloadZones();

   String getVersion();

   long getStartTime();

   int getResolverCount();

   int secondaryZoneCount();

   int primaryZoneCount();

   int getMaxActiveUDPThreadCount();

   long getCompletedUDPQueryCount();

   int getUDPThreadPoolMaxSize();

   int getUDPThreadPoolMinSize();

   int getActiveUDPThreadCount();

   int getMaxActiveTCPThreadCount();

   long getCompletedTCPQueryCount();

   int getTCPThreadPoolMaxSize();

   int getTCPThreadPoolMinSize();

   Resolver getResolver(String var1);

   List getResolvers();

   ZoneProvider getZoneProvider(String var1);

   Set getZoneProviders();

   Plugin getPlugin(String var1);

   Set getPlugins();

   long getRejectedUDPConnections();

   long getRejectedTCPConnections();

   Status getStatus();
}
