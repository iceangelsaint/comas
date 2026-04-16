package se.unlogic.eagledns.test;

import java.io.File;
import java.io.IOException;
import org.xbill.DNS.Master;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;

public class ZoneDissasembler {
   public static void main(String[] args) throws TextParseException, IOException {
      File zones = new File("zones");
      File[] zoneFiles = zones.listFiles();

      for(File zoneFile : zoneFiles) {
         System.out.println("=====" + zoneFile.getName() + "=====");
         Master master = new Master(zoneFile.getPath(), Name.fromString(zoneFile.getName(), Name.root));

         for(Record record = master.nextRecord(); record != null; record = master.nextRecord()) {
            System.out.println("Class: " + record.getClass());
            System.out.println("Name: " + record.getName());
            System.out.println("toString: " + record.toString());
            System.out.println();
         }

         master.close();
      }

   }
}
