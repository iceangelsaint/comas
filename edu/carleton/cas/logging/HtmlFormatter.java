package edu.carleton.cas.logging;

import edu.carleton.cas.resources.SystemWebResources;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

class HtmlFormatter extends Formatter {
   public static String DATE_FORMAT = "MMM dd,yyyy HH:mm:ss";
   private String title;
   private SimpleDateFormat sdf;
   private String refreshField;

   public HtmlFormatter(String title, String refreshField) {
      this(title, new SimpleDateFormat(DATE_FORMAT), refreshField);
   }

   public HtmlFormatter(String title, SimpleDateFormat sdf, String refreshField) {
      this.title = title;
      this.sdf = sdf;
      this.refreshField = refreshField;
   }

   public String format(LogRecord rec) {
      StringBuffer buf = new StringBuffer(256);
      buf.append("<tr>\n");
      buf.append("\t<td>");
      buf.append(rec.getLevel());
      buf.append("</td>\n");
      buf.append("\t<td>");
      buf.append(this.calcDate(rec.getMillis()));
      buf.append("</td>\n");
      buf.append("\t<td>");
      buf.append(this.formatMessage(rec));
      buf.append("</td>\n");
      buf.append("</tr>\n");
      return buf.toString();
   }

   private String calcDate(long millisecs) {
      Date resultdate = new Date(millisecs);
      return this.sdf.format(resultdate);
   }

   public String getHead(Handler h) {
      String var10000 = this.title;
      return "<!DOCTYPE html><html lang=\"en\">\n<head><meta charset=\"UTF-8\"><title>" + var10000 + "</title>\n" + SystemWebResources.getStylesheet() + SystemWebResources.getIcon() + "\n<style>\ntable { width: 100% }\nth { font:bold 10pt Tahoma; }\ntd { font:normal 10pt Tahoma; }\n</style>\n</head>\n<body>\n<div class=\"w3-container\"><h1 id=\"reportTitle\">" + this.title + " on " + String.valueOf(new Date()) + "</h1>\n" + this.refreshField + "<table border=\"0\" cellpadding=\"5\" cellspacing=\"3\">\n<tr align=\"left\">\n\t<th style=\"width:10%\">Log Level</th>\n\t<th style=\"width:15%\">Time</th>\n\t<th style=\"width:75%\">Log Message</th>\n</tr>\n";
   }

   public String getTail(Handler h) {
      return "</table>\n</body>\n</html>";
   }
}
