package com.cogerent.dns;

import java.net.InetAddress;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;

public interface DNSObserver {
   void onDNSLog(String var1);

   void onDNSLookup(InetAddress var1, Name var2, Record[] var3);

   void onDNSMessage(Message var1);

   void onDNSError(Throwable var1);
}
