package se.unlogic.eagledns.plugins.zonereplicator;

import java.io.Serializable;
import java.util.List;
import se.unlogic.eagledns.zoneproviders.db.beans.DBZone;
import se.unlogic.standardutils.collections.CollectionUtils;

public class ReplicationResponse implements Serializable {
   private static final long serialVersionUID = 4935055334918731111L;
   protected List newZones;
   protected List updatedZones;
   protected List deletedZones;

   public ReplicationResponse(List newZones, List updatedZones, List deletedZones) {
      this.newZones = newZones;
      this.updatedZones = updatedZones;
      this.deletedZones = deletedZones;
   }

   public List getNewZones() {
      return this.newZones;
   }

   public List getUpdatedZones() {
      return this.updatedZones;
   }

   public List getDeletedZones() {
      return this.deletedZones;
   }

   public String toString() {
      return CollectionUtils.getSize(this.newZones) + " new zones, " + CollectionUtils.getSize(this.updatedZones) + " updated zones, " + CollectionUtils.getSize(this.deletedZones) + " deleted zones";
   }
}
