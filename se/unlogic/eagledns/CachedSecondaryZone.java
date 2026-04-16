package se.unlogic.eagledns;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import org.apache.log4j.Logger;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Record;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferException;
import org.xbill.DNS.ZoneTransferIn;
import se.unlogic.eagledns.zoneproviders.ZoneProvider;

public class CachedSecondaryZone {
   private Logger log = Logger.getLogger(this.getClass());
   protected ZoneProvider zoneProvider;
   private SecondaryZone secondaryZone;

   public CachedSecondaryZone(ZoneProvider zoneProvider, SecondaryZone secondaryZone) {
      this.zoneProvider = zoneProvider;
      this.secondaryZone = secondaryZone;
      if (this.secondaryZone.getZoneCopy() != null) {
         this.log.info("Using stored zone data for sedondary zone " + this.secondaryZone.getZoneName());
      }

   }

   public SecondaryZone getSecondaryZone() {
      return this.secondaryZone;
   }

   public void setSecondaryZone(SecondaryZone secondaryZone) {
      this.secondaryZone = secondaryZone;
   }

   public void update(int axfrTimeout) {
      try {
         ZoneTransferIn xfrin = ZoneTransferIn.newAXFR(this.secondaryZone.getZoneName(), this.secondaryZone.getRemoteServerAddress(), (TSIG)null);
         xfrin.setDClass(DClass.value(this.secondaryZone.getDclass()));
         xfrin.setTimeout(Duration.ofSeconds((long)axfrTimeout));
         xfrin.run();
         if (!xfrin.isAXFR()) {
            this.log.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", response is not a valid AXFR!");
            return;
         }

         List<?> records = xfrin.getAXFR();
         Zone axfrZone = new Zone(this.secondaryZone.getZoneName(), (Record[])records.toArray(new Record[records.size()]));
         this.log.debug("Zone " + this.secondaryZone.getZoneName() + " successfully transfered from server " + this.secondaryZone.getRemoteServerAddress());
         if (!axfrZone.getSOA().getName().equals(this.secondaryZone.getZoneName())) {
            this.log.warn("Invalid AXFR zone name in response when updating secondary zone " + this.secondaryZone.getZoneName() + ". Got zone name " + axfrZone.getSOA().getName() + " in respons.");
         }

         if (this.secondaryZone.getZoneCopy() != null && this.secondaryZone.getZoneCopy().getSOA().getSerial() == axfrZone.getSOA().getSerial()) {
            this.log.info("Zone " + this.secondaryZone.getZoneName() + " is already up to date with serial " + axfrZone.getSOA().getSerial());
            this.zoneProvider.zoneChecked(this.secondaryZone);
         } else {
            this.secondaryZone.setZoneCopy(axfrZone);
            this.secondaryZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
            this.zoneProvider.zoneUpdated(this.secondaryZone);
            this.log.info("Zone " + this.secondaryZone.getZoneName() + " successfully updated from server " + this.secondaryZone.getRemoteServerAddress());
         }

         this.secondaryZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
      } catch (IOException e) {
         this.log.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
         this.checkExpired();
      } catch (ZoneTransferException e) {
         this.log.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
         this.checkExpired();
      } catch (RuntimeException e) {
         this.log.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
         this.checkExpired();
      }

   }

   private void checkExpired() {
      if (this.secondaryZone.getDownloaded() != null && this.secondaryZone.getZoneCopy() != null && System.currentTimeMillis() - this.secondaryZone.getDownloaded().getTime() > this.secondaryZone.getZoneCopy().getSOA().getExpire() * 1000L) {
         this.log.warn("AXFR copy of secondary zone " + this.secondaryZone.getZoneName() + " has expired, deleting zone data...");
         this.secondaryZone.setZoneCopy((Zone)null);
         this.secondaryZone.setDownloaded((Timestamp)null);
         this.zoneProvider.zoneUpdated(this.secondaryZone);
      }

   }
}
