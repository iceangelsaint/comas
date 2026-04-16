package se.unlogic.eagledns.zoneproviders.db;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.apache.log4j.Logger;
import org.xbill.DNS.Zone;
import se.unlogic.eagledns.SecondaryZone;
import se.unlogic.eagledns.SystemInterface;
import se.unlogic.eagledns.zoneproviders.ZoneProvider;
import se.unlogic.eagledns.zoneproviders.db.beans.DBRecord;
import se.unlogic.eagledns.zoneproviders.db.beans.DBSecondaryZone;
import se.unlogic.eagledns.zoneproviders.db.beans.DBZone;
import se.unlogic.standardutils.dao.AnnotatedDAO;
import se.unlogic.standardutils.dao.HighLevelQuery;
import se.unlogic.standardutils.dao.QueryParameterFactory;
import se.unlogic.standardutils.dao.RelationQuery;
import se.unlogic.standardutils.dao.SimpleAnnotatedDAOFactory;
import se.unlogic.standardutils.dao.SimpleDataSource;
import se.unlogic.standardutils.dao.TransactionHandler;

public class DBZoneProvider implements ZoneProvider {
   private Logger log = Logger.getLogger(this.getClass());
   private String name;
   private String driver;
   private String url;
   private String username;
   private String password;
   private AnnotatedDAO zoneDAO;
   private AnnotatedDAO recordDAO;
   private HighLevelQuery primaryZoneQuery;
   private HighLevelQuery secondaryZoneQuery;
   private QueryParameterFactory zoneIDQueryParameterFactory;
   private QueryParameterFactory recordZoneQueryParameterFactory;

   public void init(String name) throws ClassNotFoundException {
      this.name = name;

      DataSource dataSource;
      try {
         dataSource = new SimpleDataSource(this.driver, this.url, this.username, this.password);
      } catch (ClassNotFoundException e) {
         this.log.error("Unable to load JDBC driver " + this.driver, e);
         throw e;
      }

      SimpleAnnotatedDAOFactory annotatedDAOFactory = new SimpleAnnotatedDAOFactory();
      this.zoneDAO = new AnnotatedDAO(dataSource, DBZone.class, annotatedDAOFactory);
      this.recordDAO = new AnnotatedDAO(dataSource, DBRecord.class, annotatedDAOFactory);
      QueryParameterFactory<DBZone, Boolean> zoneTypeParamFactory = this.zoneDAO.getParamFactory("secondary", Boolean.TYPE);
      QueryParameterFactory<DBZone, Boolean> enabledParamFactory = this.zoneDAO.getParamFactory("enabled", Boolean.TYPE);
      this.primaryZoneQuery = new HighLevelQuery();
      this.primaryZoneQuery.addParameter(zoneTypeParamFactory.getParameter(false));
      this.primaryZoneQuery.addParameter(enabledParamFactory.getParameter(true));
      this.primaryZoneQuery.addRelation(DBZone.RECORDS_RELATION);
      this.secondaryZoneQuery = new HighLevelQuery();
      this.secondaryZoneQuery.addParameter(zoneTypeParamFactory.getParameter(true));
      this.secondaryZoneQuery.addParameter(enabledParamFactory.getParameter(true));
      this.secondaryZoneQuery.addRelation(DBZone.RECORDS_RELATION);
      this.zoneIDQueryParameterFactory = this.zoneDAO.getParamFactory("zoneID", Integer.class);
      this.recordZoneQueryParameterFactory = this.recordDAO.getParamFactory("zone", DBZone.class);
   }

   public Collection getPrimaryZones() {
      try {
         List<DBZone> dbZones = this.zoneDAO.getAll(this.primaryZoneQuery);
         if (dbZones != null) {
            ArrayList<Zone> zones = new ArrayList(dbZones.size());

            for(DBZone dbZone : dbZones) {
               try {
                  zones.addAll(dbZone.toZones());
               } catch (IOException e) {
                  this.log.error("Unable to parse zone " + dbZone.getName(), e);
               }
            }

            return zones;
         }
      } catch (SQLException e) {
         this.log.error("Error getting primary zones from DB zone provider " + this.name, e);
      }

      return null;
   }

   public Collection getSecondaryZones() {
      try {
         List<DBZone> dbZones = this.zoneDAO.getAll(this.secondaryZoneQuery);
         if (dbZones != null) {
            ArrayList<SecondaryZone> zones = new ArrayList(dbZones.size());

            for(DBZone dbZone : dbZones) {
               try {
                  DBSecondaryZone secondaryZone = new DBSecondaryZone(dbZone.getZoneID(), dbZone.getName(), dbZone.getPrimaryDNS(), dbZone.getDclass());
                  if (dbZone.getRecords() != null) {
                     secondaryZone.setZoneCopy(dbZone.toZone());
                     secondaryZone.setDownloaded(dbZone.getDownloaded());
                  }

                  zones.add(secondaryZone);
               } catch (IOException e) {
                  this.log.error("Unable to parse zone " + dbZone.getName(), e);
               }
            }

            return zones;
         }
      } catch (SQLException e) {
         this.log.error("Error getting secondary zones from DB zone provider " + this.name, e);
      }

      return null;
   }

   public void zoneUpdated(SecondaryZone zone) {
      if (!(zone instanceof DBSecondaryZone)) {
         this.log.warn(zone.getClass() + " is not an instance of " + DBSecondaryZone.class + ", ignoring zone update");
      } else {
         Integer zoneID = ((DBSecondaryZone)zone).getZoneID();
         TransactionHandler transactionHandler = null;

         try {
            transactionHandler = this.zoneDAO.createTransaction();
            DBZone dbZone = (DBZone)this.zoneDAO.get(new HighLevelQuery(this.zoneIDQueryParameterFactory.getParameter(zoneID), new Field[]{null}), transactionHandler);
            if (dbZone == null) {
               this.log.warn("Unable to find secondary zone with zoneID " + zoneID + " in DB, ignoring zone update");
               return;
            }

            if (!dbZone.isEnabled()) {
               this.log.warn("Secondary zone with zone " + dbZone + " is disabled in DB ignoring AXFR update");
               return;
            }

            dbZone.parse(zone.getZoneCopy(), true);
            this.zoneDAO.update(dbZone, transactionHandler, (RelationQuery)null);
            this.recordDAO.delete(new HighLevelQuery(this.recordZoneQueryParameterFactory.getParameter(dbZone), new Field[]{null}), transactionHandler);
            if (dbZone.getRecords() != null) {
               for(DBRecord dbRecord : dbZone.getRecords()) {
                  dbRecord.setZone(dbZone);
                  this.recordDAO.add(dbRecord, transactionHandler, (RelationQuery)null);
               }
            }

            transactionHandler.commit();
            this.log.debug("Changes in seconday zone " + dbZone + " saved");
         } catch (SQLException e) {
            this.log.error("Unable to save changes in secondary zone " + zone.getZoneName(), e);
            TransactionHandler.autoClose(transactionHandler);
         }

      }
   }

   public void zoneChecked(SecondaryZone zone) {
      if (!(zone instanceof DBSecondaryZone)) {
         this.log.warn(zone.getClass() + " is not an instance of " + DBSecondaryZone.class + ", ignoring zone check");
      } else {
         Integer zoneID = ((DBSecondaryZone)zone).getZoneID();
         TransactionHandler transactionHandler = null;

         try {
            transactionHandler = this.zoneDAO.createTransaction();
            DBZone dbZone = (DBZone)this.zoneDAO.get(new HighLevelQuery(this.zoneIDQueryParameterFactory.getParameter(zoneID), new Field[]{null}), transactionHandler);
            if (dbZone == null) {
               this.log.warn("Unable to find secondary zone with zoneID " + zoneID + " in DB, ignoring zone check");
               return;
            }

            if (!dbZone.isEnabled()) {
               this.log.warn("Secondary zone with zone " + dbZone + " is disabled in DB ignoring zone check");
               return;
            }

            dbZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
            this.zoneDAO.update(dbZone, transactionHandler, (RelationQuery)null);
            transactionHandler.commit();
            this.log.debug("Download timestamp of seconday zone " + dbZone + " updated");
         } catch (SQLException e) {
            this.log.error("Unable to update download of secondary zone " + zone.getZoneName(), e);
            TransactionHandler.autoClose(transactionHandler);
         }

      }
   }

   public void shutdown() {
   }

   public void setDriver(String driver) {
      this.driver = driver;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   public void setSystemInterface(SystemInterface systemInterface) {
   }
}
