package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.processors.ExamQuestionProperties;
import edu.carleton.cas.resources.AbstractFileTask;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class ExamServlet extends EmbeddedServlet {
   public static final String DATE_TIME_FORMAT = "yyyy/MM/dd hh:mm:ss a";
   private static final DateFormatSymbols DFS = new DateFormatSymbols();
   private static final SimpleDateFormat SDF;
   private static final int EARLIEST_QUESTION_COOKIE_TIME_IN_MINUTES = 5;
   private static final FileFilter EXAM_FILE_FILTER = new FileFilter() {
      public boolean accept(File f) {
         String name = f.getName();
         if (!f.isHidden() && f.canRead() && f.canWrite()) {
            if (name.startsWith("~")) {
               return false;
            } else if (name.equals("activity.idx")) {
               return false;
            } else if (name.endsWith(".txt") && (new File(Utils.removeExtensionFull(f) + ".html")).exists()) {
               return false;
            } else {
               for(String type : AbstractFileTask.getFileTypes()) {
                  if (name.endsWith(type)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      }
   };

   static {
      DFS.setAmPmStrings(new String[]{"AM", "PM"});
      SDF = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", DFS);
   }

   public ExamServlet(Invigilator invigilator) {
      super(invigilator);
      invigilator.setExamQuestionProperties(new ExamQuestionProperties(invigilator));
   }

   public String getMapping() {
      return this.invigilator.getSessionContext();
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      ServletHolder sh = sp.addServlet(this, "/" + this.getMapping());
      sp.getRouter().addRule("/" + this.getMapping(), InvigilatorState.running);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.invigilator.setLastServlet(this.getMapping());
         Cookie[] cookies = request.getCookies();
         int statusCode = 200;
         String mimeType = "text/html";
         String resource = ClientShared.getExamDirectory(this.invigilator.getCourse(), this.invigilator.getActivity());
         File f = new File(resource);
         if (f.exists() && f.isDirectory() && f.canRead()) {
            int numberOfUnarchivedFiles = 0;
            File[] files = f.listFiles(EXAM_FILE_FILTER);
            boolean fullNameRequired = this.checkForDuplicates(files);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType(mimeType);
            PrintWriter wr = response.getWriter();
            wr.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>");
            wr.print(this.invigilator.getTitle());
            wr.print("CoMaS ");
            wr.print("Exam for ");
            wr.print(this.invigilator.getSessionContext());
            wr.println("</title>");
            wr.println(SystemWebResources.getStylesheet());
            wr.println(SystemWebResources.getIcon());
            wr.println("</head><body><div class=\"w3-container\"><div class=\"w3-panel\" style=\"margin:auto;width:80%\">");
            wr.print("<h1 id=\"reportTitle\">");
            wr.print("Exam for ");
            wr.print(this.invigilator.getSessionContext());
            wr.print(" for ");
            wr.print(this.invigilator.getName());
            wr.println("</h1>");
            wr.println(this.invigilator.getServletProcessor().checkForServletCode());
            wr.println(this.invigilator.getServletProcessor().refreshForServlet());
            if (files != null) {
               String var29;
               if (this.invigilator.isRestartedSession()) {
                  SimpleDateFormat var28 = SDF;
                  var29 = "Question (Restarted " + var28.format(new Date(this.invigilator.getRestartedArchiveSessionTime())) + ")";
               } else {
                  var29 = "Question";
               }

               String header = var29;
               wr.print("<table class=\"w3-table-all\"><thead><th>");
               wr.print(header);
               wr.println("</th><th id=\"saved-column-header\">Saved</th><th>Age</th></thead><tbody>");
               long lastUploadTime = Math.max(UploadServlet.getSingleton().getLastUploadTime(), UploadCheckServlet.getSingleton().getLastModified());

               for(File file : files) {
                  long modificationTime = this.processExamFile(wr, file, fullNameRequired, cookies);
                  if (ClientShared.AUTO_ARCHIVE && modificationTime > lastUploadTime) {
                     ++numberOfUnarchivedFiles;
                  }
               }

               wr.println("</tbody>");
               wr.println("</table>");
            }

            String examPage = this.invigilator.getServletProcessor().getService(this.getMapping());
            String uploadPage = this.invigilator.getServletProcessor().getService(UploadServlet.getMapping());
            String uploadCheckPage = this.invigilator.getServletProcessor().getService("tools/upload.html");
            wr.println("<div style=\"padding:16px\"><button accesskey=\"r\" class=\"w3-button w3-round-large w3-green\" onclick='goTo(\"" + examPage + "\", \"Exam\");'>Refresh</button>&nbsp;");
            if (ClientShared.AUTO_ARCHIVE) {
               long lastUploadTime = UploadServlet.getSingleton().getLastUploadTime();
               File archiveFile = this.invigilator.archiveFile();
               if (lastUploadTime == 0L && !archiveFile.exists()) {
                  wr.println("<button accesskey=\"u\" class=\"w3-button w3-round-large w3-green\" onclick='goTo(\"" + uploadPage + "\", \"Exam\");'>Upload</button></div>");
               } else {
                  wr.print("Uploaded: ");
                  wr.print(SDF.format(new Date(UploadCheckServlet.getSingleton().getLastModified())));
                  wr.print(" (");
                  if (archiveFile.exists()) {
                     wr.print(archiveFile.length());
                  } else {
                     wr.print(UploadCheckServlet.getSingleton().getSize());
                  }

                  wr.print(" bytes)");
                  String toolsFolder = ClientShared.getToolsDirectory(this.invigilator.getCourse(), this.invigilator.getActivity());
                  File uploadCheckTool = new File(toolsFolder, "upload.html");
                  if (!uploadCheckTool.exists() && !uploadCheckTool.canRead()) {
                     uploadCheckPage = this.invigilator.getServletProcessor().getService(UploadCheckServlet.getMapping());
                  }

                  wr.print("&nbsp;");
                  if (System.currentTimeMillis() - lastUploadTime > (long)ClientShared.MIN_MSECS_BETWEEN_USER_UPLOADS) {
                     wr.println("<button accesskey=\"u\" class=\"w3-button w3-round-large w3-green\" onclick='goTo(\"" + uploadPage + "\", \"Exam\");'>Upload</button>");
                     wr.print("&nbsp;");
                  }

                  wr.print("<button accesskey=\"k\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"");
                  wr.print(uploadCheckPage);
                  wr.print("\", \"Exam\");'>Check Upload");
                  if (!uploadCheckTool.exists() && !uploadCheckTool.canRead()) {
                     wr.print(" ");
                     wr.print(UploadCheckServlet.getSingleton().getStatusString());
                  }

                  wr.println("</button>");
                  wr.println("</div>");
               }
            }

            if (numberOfUnarchivedFiles > 0) {
               wr.println("<script>");
               wr.print("const idColumnHeader = document.getElementById(\"saved-column-header\");\n");
               wr.print("idColumnHeader.innerHTML +=");
               wr.print("'&nbsp;<span class=\"w3-badge ");
               String badgeColour;
               if (numberOfUnarchivedFiles == 1) {
                  badgeColour = "w3-yellow";
               } else if (numberOfUnarchivedFiles == 2) {
                  badgeColour = "w3-orange";
               } else {
                  badgeColour = "w3-red";
               }

               wr.print(badgeColour);
               wr.print("\">");
               wr.print(numberOfUnarchivedFiles);
               wr.print("</span>';\n");
               wr.println("</script>");
            }

            wr.println(this.invigilator.getServletProcessor().footerForServlet(true, true, SystemWebResources.getHomeButton()));
            wr.println("</div></div></body></html>");
         } else {
            PrintWriter var10000 = response.getWriter();
            String var10001 = SystemWebResources.getStylesheet();
            var10000.println("<html><head>" + var10001 + SystemWebResources.getIcon() + "</head><body><h1>The " + this.getMapping() + " folder is not accessible</h1></body></html>");
            statusCode = 404;
         }

         response.setStatus(statusCode);
      }
   }

   private boolean checkForDuplicates(File[] files) {
      if (files != null && files.length != 1) {
         for(int i = 0; i < files.length - 1; ++i) {
            String f = Utils.removeExtension(files[i]);

            for(int j = i + 1; j < files.length; ++j) {
               if (files[j].getName().startsWith(f)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private long processExamFile(PrintWriter wr, File file, boolean fullNameRequired, Cookie[] cookies) {
      String var10000 = file.getParentFile().getName();
      String location = var10000 + "/" + file.getName();
      wr.print("<tr>");
      wr.print("<td>");
      boolean isQuestionFile = this.invigilator.getExamQuestionProperties().containsKey(file.getName());
      if (isQuestionFile) {
         wr.print("<div><button class=\"w3-button w3-round-large w3-green\" onclick='goTo(\"/");
      } else {
         wr.print("<div><button class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"/");
      }

      wr.print(location);
      wr.print("\", \"");
      wr.print(Utils.removeExtension(file) + " question");
      wr.print("\");'>");
      String nameOfQuestion = this.invigilator.getExamQuestionProperties().getLabel(file.getName(), fullNameRequired ? file.getName() : Utils.removeExtension(file));
      wr.print(nameOfQuestion);
      wr.print("</button>");
      wr.println("</div>");
      wr.print("</td>");
      var10000 = this.invigilator.getCourse();
      String name = var10000 + "-" + this.invigilator.getActivity() + "-" + file.getName();
      boolean found = false;
      long valueAsLong = 0L;
      if (cookies != null) {
         for(Cookie entry : cookies) {
            String cookieName = entry.getName();
            if (cookieName.equals(name) || cookieName.equals(file.getName())) {
               String tokenValue = entry.getValue();

               try {
                  valueAsLong = Long.parseLong(tokenValue);
                  long ageInMsecs = System.currentTimeMillis() - valueAsLong;
                  if (ageInMsecs < this.invigilator.getExamDurationInMinutes() * 60L * 1000L && valueAsLong > this.invigilator.getActualStartTime() || Math.abs(this.invigilator.getActualStartTime() - valueAsLong) < 300000L) {
                     this.printDate(wr, valueAsLong, ageInMsecs);
                     found = true;
                     break;
                  }
               } catch (NumberFormatException var20) {
                  valueAsLong = 0L;
               }
            }
         }
      }

      if (!found) {
         long ageInMsecs = System.currentTimeMillis() - file.lastModified();
         if (ageInMsecs < this.invigilator.getExamDurationInMinutes() * 60L * 1000L && file.lastModified() > this.invigilator.getActualStartTime() || this.invigilator.isRestartedSession()) {
            this.printDate(wr, file.lastModified(), ageInMsecs);
            valueAsLong = file.lastModified();
            found = true;
         }
      }

      if (!found) {
         wr.print("<td></td><td></td>");
      }

      wr.print("</tr>");
      return valueAsLong;
   }

   private void printDate(PrintWriter wr, long valueAsLong, long ageInMsecs) {
      wr.print("<td>");
      wr.print(SDF.format(new Date(valueAsLong)));
      wr.print("</td><td>");
      wr.print(Utils.convertMsecsToHoursMinutesSeconds(ageInMsecs));
      wr.print("</td>");
   }
}
