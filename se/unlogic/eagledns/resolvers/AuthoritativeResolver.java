package se.unlogic.eagledns.resolvers;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.RRSIGRecord;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;
import se.unlogic.eagledns.EagleDNS;
import se.unlogic.eagledns.Request;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.standardutils.net.SocketUtils;

public class AuthoritativeResolver extends BasePlugin implements Resolver {
   public Message generateReply(Request request) throws Exception {
      Message query = request.getQuery();
      Record queryRecord = query.getQuestion();
      if (queryRecord == null) {
         return null;
      } else {
         Name name = queryRecord.getName();
         Zone zone = this.findBestZone(name);
         if (zone != null) {
            this.log.debug("Resolver " + this.name + " processing request for " + name + ", matching zone found ");
            int flags = 0;
            Header header = query.getHeader();
            if (header.getFlag(0)) {
               return null;
            } else if (header.getRcode() != 0) {
               return null;
            } else if (header.getOpcode() != 0) {
               return null;
            } else {
               TSIGRecord queryTSIG = query.getTSIG();
               TSIG tsig = null;
               if (queryTSIG != null) {
                  tsig = this.systemInterface.getTSIG(queryTSIG.getName());
                  if (tsig == null || tsig.verify(query, request.getRawQuery(), (TSIGRecord)null) != 0) {
                     return null;
                  }
               }

               OPTRecord queryOPT = query.getOPT();
               if (queryOPT != null && (queryOPT.getFlags() & '耀') != 0) {
                  flags = 1;
               }

               Message response = new Message(query.getHeader().getID());
               response.getHeader().setFlag(0);
               if (query.getHeader().getFlag(7)) {
                  response.getHeader().setFlag(7);
               }

               response.addRecord(queryRecord, 0);
               int type = queryRecord.getType();
               int dclass = queryRecord.getDClass();
               if (type == 252 && request.getSocket() != null) {
                  return this.doAXFR(name, query, tsig, queryTSIG, request.getSocket());
               } else if (!Type.isRR(type) && type != 255) {
                  return null;
               } else {
                  byte rcode = this.addAnswer(response, name, type, dclass, 0, flags, zone);
                  if (rcode != 0 && rcode != 3) {
                     return EagleDNS.errorMessage(query, rcode);
                  } else {
                     this.addAdditional(response, flags);
                     if (queryOPT != null) {
                        int optflags = flags == 1 ? '耀' : 0;
                        OPTRecord opt = new OPTRecord(4096, rcode, 0, optflags);
                        response.addRecord(opt, 3);
                     }

                     response.setTSIG(tsig, 0, queryTSIG);
                     return response;
                  }
               }
            }
         } else {
            this.log.debug("Resolver " + this.name + " ignoring request for " + name + ", no matching zone found");
            return null;
         }
      }
   }

   private final void addAdditional(Message response, int flags) {
      this.addAdditional2(response, 1, flags);
      this.addAdditional2(response, 2, flags);
   }

   private byte addAnswer(Message response, Name name, int type, int dclass, int iterations, int flags, Zone zone) {
      byte rcode = 0;
      if (iterations > 6) {
         return 0;
      } else {
         if (type == 24 || type == 46) {
            type = 255;
            flags |= 2;
         }

         if (zone == null) {
            zone = this.findBestZone(name);
         }

         if (zone != null) {
            SetResponse sr = zone.findRecords(name, type);
            if (sr.isNXDOMAIN()) {
               response.getHeader().setRcode(3);
               if (zone != null) {
                  this.addSOA(response, zone);
                  if (iterations == 0) {
                     response.getHeader().setFlag(5);
                  }
               }

               rcode = 3;
            } else if (sr.isNXRRSET()) {
               if (zone != null) {
                  this.addSOA(response, zone);
                  if (iterations == 0) {
                     response.getHeader().setFlag(5);
                  }
               }
            } else if (sr.isDelegation()) {
               RRset nsRecords = sr.getNS();
               this.addRRset(nsRecords.getName(), response, nsRecords, 2, flags);
            } else if (sr.isCNAME()) {
               CNAMERecord cname = sr.getCNAME();
               RRset rrset = new RRset(cname);
               this.addRRset(name, response, rrset, 1, flags);
               if (zone != null && iterations == 0) {
                  response.getHeader().setFlag(5);
               }

               rcode = this.addAnswer(response, cname.getTarget(), type, dclass, iterations + 1, flags, (Zone)null);
            } else if (sr.isDNAME()) {
               DNAMERecord dname = sr.getDNAME();
               RRset rrset = new RRset(dname);
               this.addRRset(name, response, rrset, 1, flags);

               Name newname;
               try {
                  newname = name.fromDNAME(dname);
               } catch (NameTooLongException var14) {
                  return 6;
               }

               rrset = new RRset(new CNAMERecord(name, dclass, 0L, newname));
               this.addRRset(name, response, rrset, 1, flags);
               if (zone != null && iterations == 0) {
                  response.getHeader().setFlag(5);
               }

               rcode = this.addAnswer(response, newname, type, dclass, iterations + 1, flags, (Zone)null);
            } else if (sr.isSuccessful()) {
               for(RRset rrset : sr.answers()) {
                  this.addRRset(name, response, rrset, 1, flags);
               }

               if (zone != null) {
                  this.addNS(response, zone, flags);
                  if (iterations == 0) {
                     response.getHeader().setFlag(5);
                  }
               }
            }
         }

         return rcode;
      }
   }

   private Message doAXFR(Name name, Message query, TSIG tsig, TSIGRecord qtsig, Socket socket) {
      boolean first = true;
      Zone zone = this.findBestZone(name);
      if (zone == null) {
         return EagleDNS.errorMessage(query, 5);
      } else {
         boolean axfrAllowed = false;

         for(Record nsRecord : zone.getNS().rrs()) {
            NSRecord record = (NSRecord)nsRecord;

            try {
               String nsIP = InetAddress.getByName(record.getTarget().toString()).getHostAddress();
               if (socket.getInetAddress().getHostAddress().equals(nsIP)) {
                  axfrAllowed = true;
                  break;
               }
            } catch (UnknownHostException var23) {
               this.log.warn("Unable to resolve hostname of nameserver " + record.getTarget() + " in zone " + zone.getOrigin() + " while processing AXFR request from " + socket.getRemoteSocketAddress());
            }
         }

         if (!axfrAllowed) {
            this.log.warn("AXFR request of zone " + zone.getOrigin() + " from " + socket.getRemoteSocketAddress() + " refused!");
            return EagleDNS.errorMessage(query, 5);
         } else {
            Iterator<?> it = zone.AXFR();

            try {
               DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
               int id = query.getHeader().getID();

               while(it.hasNext()) {
                  RRset rrset = (RRset)it.next();
                  Message response = new Message(id);
                  Header header = response.getHeader();
                  header.setFlag(0);
                  header.setFlag(5);
                  this.addRRset(rrset.getName(), response, rrset, 1, 1);
                  if (tsig != null) {
                     tsig.apply(response, qtsig, first);
                     qtsig = response.getTSIG();
                  }

                  first = false;
                  byte[] out = response.toWire();
                  dataOut.writeShort(out.length);
                  dataOut.write(out);
               }
            } catch (IOException ex) {
               this.log.warn("AXFR failed", ex);
            } finally {
               SocketUtils.closeSocket(socket);
            }

            return null;
         }
      }
   }

   private final void addSOA(Message response, Zone zone) {
      response.addRecord(zone.getSOA(), 2);
   }

   private final void addNS(Message response, Zone zone, int flags) {
      RRset nsRecords = zone.getNS();
      this.addRRset(nsRecords.getName(), response, nsRecords, 2, flags);
   }

   private void addGlue(Message response, Name name, int flags) {
      RRset a = this.findExactMatch(name, 1, 1, true);
      if (a != null) {
         this.addRRset(name, response, a, 3, flags);
      }
   }

   private void addAdditional2(Message response, int section, int flags) {
      for(Record r : response.getSection(section)) {
         Name glueName = r.getAdditionalName();
         if (glueName != null) {
            this.addGlue(response, glueName, flags);
         }
      }

   }

   private RRset findExactMatch(Name name, int type, int dclass, boolean glue) {
      Zone zone = this.findBestZone(name);
      return zone != null ? zone.findExactMatch(name, type) : null;
   }

   private void addRRset(Name name, Message response, RRset rrset, int section, int flags) {
      for(int s = 1; s <= section; ++s) {
         if (response.findRRset(name, rrset.getType(), s)) {
            return;
         }
      }

      if ((flags & 2) == 0) {
         for(Record ar : rrset.rrs()) {
            Record r = ar;
            if (ar.getName().isWild() && !name.isWild()) {
               r = ar.withName(name);
            }

            response.addRecord(r, section);
         }
      }

      if ((flags & 3) != 0) {
         for(RRSIGRecord rrsigrecord : rrset.sigs()) {
            Record r = rrsigrecord;
            if (((Record)rrsigrecord).getName().isWild() && !name.isWild()) {
               r = ((Record)rrsigrecord).withName(name);
            }

            response.addRecord(r, section);
         }
      }

   }

   private Zone findBestZone(Name name) {
      Zone foundzone = this.systemInterface.getZone(name);
      if (foundzone != null) {
         return foundzone;
      } else {
         int labels = name.labels();

         for(int i = 1; i < labels; ++i) {
            Name tname = new Name(name, i);
            foundzone = this.systemInterface.getZone(tname);
            if (foundzone != null) {
               return foundzone;
            }
         }

         return null;
      }
   }
}
