package se.unlogic.eagledns;

import java.sql.Timestamp;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Zone;

public class SecondaryZone {
   private Name zoneName;
   private String remoteServerAddress;
   private String dclass;
   private Timestamp downloaded;
   private Zone zoneCopy;

   public SecondaryZone(String zoneName, String remoteServerAddress, String dclass) throws TextParseException {
      this.zoneName = Name.fromString(zoneName, Name.root);
      this.remoteServerAddress = remoteServerAddress;
      this.dclass = dclass;
   }

   public SecondaryZone(String zoneName, String remoteServerAddress, String dclass, Timestamp zoneDownloaded, Zone zone) throws TextParseException {
      this.zoneName = Name.fromString(zoneName, Name.root);
      this.remoteServerAddress = remoteServerAddress;
      this.dclass = dclass;
      this.zoneCopy = zone;
      this.downloaded = zoneDownloaded;
   }

   public Name getZoneName() {
      return this.zoneName;
   }

   public void setZoneName(Name zoneName) {
      this.zoneName = zoneName;
   }

   public String getRemoteServerAddress() {
      return this.remoteServerAddress;
   }

   public void setRemoteServerAddress(String remoteServerIP) {
      this.remoteServerAddress = remoteServerIP;
   }

   public Zone getZoneCopy() {
      return this.zoneCopy;
   }

   public void setZoneCopy(Zone zone) {
      this.zoneCopy = zone;
   }

   public String getDclass() {
      return this.dclass;
   }

   public void setDclass(String dclass) {
      this.dclass = dclass;
   }

   public Timestamp getDownloaded() {
      return this.downloaded;
   }

   public void setDownloaded(Timestamp zoneDownloaded) {
      this.downloaded = zoneDownloaded;
   }
}
