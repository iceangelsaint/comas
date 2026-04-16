package se.unlogic.eagledns.resolvers;

import org.xbill.DNS.Message;
import se.unlogic.eagledns.Request;
import se.unlogic.eagledns.plugins.Plugin;

public interface Resolver extends Plugin {
   Message generateReply(Request var1) throws Exception;
}
