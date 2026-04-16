package edu.carleton.cas.jetty.embedded;

import com.cogerent.utility.PropertyValue;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import edu.carleton.cas.file.Utils;
import edu.carleton.cas.logging.Level;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.resources.Configurable;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletHolder;

@MultipartConfig
public class GazeServlet extends EmbeddedWebcamServlet implements Configurable {
   private static GazeServlet singleton;
   private static String DEFAULT_CHART_SCRIPT = "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.9.1/chart.min.js\" integrity=\"sha512-ElRFoEQdI5Ht6kZvyzXhYG9NqjtkmlkfYk0wr6wHxU9JEHakS7UJZNeml5ALk+8IKlU6jDgMabC3vkumRokgJA==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"></script>";
   private static String CHART_SCRIPT;
   private final ConcurrentHashMap buckets;
   private boolean includingFaceDetection = false;
   private boolean includingGazeDetection = false;
   private boolean includingGazeEyeReport = false;
   private boolean includingAllGazeData = false;
   private Gson gson;

   static {
      CHART_SCRIPT = DEFAULT_CHART_SCRIPT;
   }

   public GazeServlet(Invigilator invigilator) {
      super(invigilator);
      this.configure(invigilator.getProperties());
      this.buckets = new ConcurrentHashMap();
      this.gson = new Gson();
      if (singleton == null) {
         singleton = this;
      }

   }

   public static String getMapping() {
      return "gaze";
   }

   public void addServletHandler() {
      ServletProcessor sp = this.invigilator.getServletProcessor();
      String myMapping = "/" + getMapping();
      ServletHolder sh = sp.addServlet(this, myMapping);
      ServletRouter sr = sp.getRouter();
      sr.addRule(myMapping, InvigilatorState.running);
      sh.getRegistration().setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), this.maxFileSize, this.maxRequestSize, this.fileSizeThreshold));
   }

   public static GazeServlet getSingleton() {
      return singleton;
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      if (this.invigilator.getServletProcessor().getRouter().canAccessOrRedirect(request, response)) {
         this.getForm(request, response, "");
         this.invigilator.setLastServlet(getMapping());
      }

   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      this.invigilator.getServletProcessor().updateLastAccessTime();
      String type = request.getParameter("type");
      String _token = request.getParameter("token");
      if (type != null && _token != null) {
         if (_token.equals(WebcamServlet.getSingleton().token)) {
            if (this.includingGazeDetection) {
               if (type.equals("eye")) {
                  this.processEye(request, response);
               } else {
                  response.setStatus(500);
               }

            }
         }
      }
   }

   private synchronized void processEye(HttpServletRequest request, HttpServletResponse response) {
      Category leftEyeCategory;
      try {
         String leftEyeCategoryString = request.getParameter("leftEye");
         if (leftEyeCategoryString == null) {
            return;
         }

         leftEyeCategory = (Category)this.gson.fromJson(leftEyeCategoryString, Category.class);
         leftEyeCategory.canonicalize();
      } catch (JsonSyntaxException var22) {
         return;
      }

      Category rightEyeCategory;
      try {
         String rightEyeCategoryString = request.getParameter("rightEye");
         if (rightEyeCategoryString == null) {
            return;
         }

         rightEyeCategory = (Category)this.gson.fromJson(rightEyeCategoryString, Category.class);
         rightEyeCategory.canonicalize();
      } catch (JsonSyntaxException var21) {
         return;
      }

      try {
         String allScores = request.getParameter("all");
         if (allScores == null) {
            return;
         }

         Category[] categories = (Category[])this.gson.fromJson(allScores, Category[].class);

         for(Category category : categories) {
            category.canonicalize();
         }

         float maxScore = Float.MIN_VALUE;
         String maxCategory = "";

         for(Category categoryRight : categories) {
            if (categoryRight.getCategoryName().contains("Right")) {
               for(Category categoryLeft : categories) {
                  if (categoryLeft.getCategoryName().contains("Left")) {
                     String key = this.gazeKey(categoryRight.getCategoryName(), categoryLeft.getCategoryName());
                     float score = categoryRight.getScore() * categoryLeft.getScore();
                     if (score > maxScore) {
                        maxScore = score;
                        maxCategory = key;
                     }

                     if (this.buckets.containsKey(key)) {
                        FrequencyAndTotalScore fats = (FrequencyAndTotalScore)this.buckets.get(key);
                        fats.add((double)score);
                     } else {
                        FrequencyAndTotalScore fats = new FrequencyAndTotalScore();
                        fats.add((double)score);
                        this.buckets.put(key, fats);
                     }
                  }
               }
            }
         }

         FrequencyAndTotalScore maxFat = (FrequencyAndTotalScore)this.buckets.get(maxCategory);
         if (maxFat != null) {
            maxFat.incr((double)maxScore);
         }
      } catch (JsonSyntaxException var20) {
         return;
      }

      this.addData(leftEyeCategory.getCategoryName(), leftEyeCategory.getScore());
      this.addData(rightEyeCategory.getCategoryName(), rightEyeCategory.getScore());
      String key = this.gazeKey(leftEyeCategory.getCategoryName(), rightEyeCategory.getCategoryName());
      float value = rightEyeCategory.getScore() * leftEyeCategory.getScore();
      this.addData(key, value);
   }

   private void addData(String key, float value) {
      if (this.buckets.containsKey(key)) {
         FrequencyAndTotalScore fats = (FrequencyAndTotalScore)this.buckets.get(key);
         fats.increment((double)value);
      } else {
         this.buckets.put(key, new FrequencyAndTotalScore(1, (double)value));
      }

   }

   private String gazeKey(String leftEye, String rightEye) {
      return leftEye + " " + rightEye;
   }

   private void getForm(HttpServletRequest request, HttpServletResponse response, String extraHTML) throws IOException {
      response.addHeader("Access-Control-Allow-Origin", this.invigilator.getServletProcessor().getService());
      response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
      response.setContentType("text/html");
      PrintWriter wr = response.getWriter();
      wr.print("<!DOCTYPE html>\n<html lang=\"en\">\n <head>\n  <title>");
      wr.print(this.invigilator.getTitle());
      wr.print(this.tabTitle());
      wr.print("</title>\n");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println(SystemWebResources.getFonts());
      wr.print("</head>\n<body>\n");
      wr.println(this.invigilator.getServletProcessor().checkForServletCode((String)null));
      wr.println("<div class=\"w3-container w3-center\">");
      wr.print("<h1><img alt=\"CoMaS logo\" src=\"");
      wr.print(SystemWebResources.getAppImage());
      wr.print("\"></h1>\n");
      wr.print(this.invigilator.getServletProcessor().refreshForServlet());
      wr.print("  <input type=\"hidden\" id=\"token\" name=\"token\" value=\"");
      wr.print(this.token);
      wr.print("\" />\n");
      wr.print("<p id=\"status\"></p>");
      wr.print("</div>");
      wr.print(this.report());
      ServletProcessor var10001 = this.invigilator.getServletProcessor();
      String var10004 = this.invigilator.getServletProcessor().getMailButtonSeparatorAfter();
      wr.println(var10001.footerForServlet(true, true, var10004 + SystemWebResources.getHomeButton("")));
      wr.print("</body></html> ");
   }

   public synchronized String report() {
      if (!this.includingGazeDetection) {
         return "<h1 style=\"text-align:center\">Gaze reporting has not been enabled for this session</h1>";
      } else {
         StringBuffer sb = new StringBuffer(1024);
         sb.append(CHART_SCRIPT);
         String[] allKeysRight = new String[]{"UpRight", "DownRight", "InRight", "OutRight"};
         String[] allKeysLeft = new String[]{"UpLeft", "DownLeft", "InLeft", "OutLeft"};
         sb.append("<div class=\"w3-container w3-center\">\n");
         String canvasID = "gaze-categories";
         sb.append("\n<canvas id=\"");
         sb.append(canvasID);
         sb.append("\" style=\"margin-left:30%;margin-right:30%;width:40%;\"></canvas>\n");
         if (this.includingAllGazeData) {
            FrequencyAndTotalScore v = (FrequencyAndTotalScore)this.buckets.get(this.gazeKey(allKeysRight[0], allKeysLeft[0]));
            if (v != null) {
               this.printDataTableHeader(sb, "Full Gaze Data (" + v.samples() + " samples)");
               this.printDataRows(sb, allKeysRight, allKeysLeft);
               this.printTableTail(sb);
            }
         } else {
            this.printTableHeader(sb, "Gaze Category");
            this.printRows(sb, allKeysLeft, allKeysRight);
            this.printTableTail(sb);
         }

         if (this.includingGazeEyeReport) {
            this.printTableHeader(sb, "Eye Category");
            this.printRows(sb, allKeysLeft);
            this.printRows(sb, allKeysRight);
            this.printTableTail(sb);
         }

         sb.append("</div>\n");
         sb.append(this.chartScript(allKeysLeft, allKeysRight, canvasID));
         return sb.toString();
      }
   }

   private void printDataTableHeader(StringBuffer sb, String header) {
      this.printTableHeaderCore(sb, header, "<th id=\"gaze\">CATEGORY</th><th id=\"gaze\">FREQUENCY</th><th id=\"gaze\">MAX</th><th id=\"gaze\">MIN</th><th id=\"gaze\">MEAN</th><th id=\"gaze\">STD.DEV.</th><th id=\"gaze\">PERCENT</th>", "gaze");
   }

   private void printTableHeader(StringBuffer sb, String header) {
      this.printTableHeaderCore(sb, header, "<th id=\"gaze\">CATEGORY</th><th id=\"gaze\">FREQUENCY</th><th id=\"gaze\">SCORE</th><th id=\"gaze\">PERCENT</th>", "gaze");
   }

   private void printTableHeaderCore(StringBuffer sb, String header, String core, String id) {
      sb.append("<h5>");
      sb.append(header);
      sb.append("</h5>\n");
      sb.append("<table class=\"w3-table-all\" style=\"margin-left:30%;margin-right:30%;width:40%;\">\n");
      sb.append("<thead>");
      sb.append(core);
      sb.append("</thead>\n");
      sb.append("<tbody>\n");
      sb.append("<script>\n");
      sb.append(SystemWebResources.sorting(id));
      sb.append("</script>");
   }

   private void printTableTail(StringBuffer sb) {
      sb.append("</tbody></table><br/>\n");
   }

   private void printDataRows(StringBuffer sb, String[] keysLeft, String[] keysRight) {
      int index = 0;
      String[] keys = new String[keysLeft.length * keysRight.length];

      for(int keyL = 0; keyL < keysLeft.length; ++keyL) {
         for(int keyR = 0; keyR < keysRight.length; ++keyR) {
            keys[index] = this.gazeKey(keysLeft[keyL], keysRight[keyR]);
            ++index;
         }
      }

      this.printDataRows(sb, keys);
   }

   private void printRows(StringBuffer sb, String[] keysLeft, String[] keysRight) {
      int index = 0;
      String[] keys = new String[keysLeft.length * keysRight.length];

      for(int keyL = 0; keyL < keysLeft.length; ++keyL) {
         for(int keyR = 0; keyR < keysRight.length; ++keyR) {
            keys[index] = this.gazeKey(keysLeft[keyL], keysRight[keyR]);
            ++index;
         }
      }

      this.printRows(sb, keys);
   }

   private void printDataRows(StringBuffer sb, String[] keys) {
      FrequencyAndTotalScore max = new FrequencyAndTotalScore(0, (double)0.0F);
      String maxCategory = keys[0];
      int maxFrequency = 0;
      int total = 0;

      for(String key : keys) {
         FrequencyAndTotalScore v = (FrequencyAndTotalScore)this.buckets.getOrDefault(key, max);
         total += v.frequency;
         if (v.frequency > maxFrequency) {
            maxCategory = key;
            maxFrequency = v.frequency;
         }
      }

      for(String key : keys) {
         sb.append("<tr><td");
         if (key.equals(maxCategory)) {
            sb.append(" style=\"color: red\"");
         }

         sb.append(">");
         sb.append(key);
         sb.append("</td>");
         FrequencyAndTotalScore v = (FrequencyAndTotalScore)this.buckets.getOrDefault(key, max);
         sb.append("<td>");
         sb.append(v.frequency);
         sb.append("</td>");
         sb.append("<td>");
         sb.append(String.format("%.04f", v.max()));
         sb.append("</td>");
         sb.append("<td>");
         sb.append(String.format("%.04f", v.min()));
         sb.append("</td>");
         sb.append("<td>");
         sb.append(String.format("%.04f", v.mean()));
         sb.append("</td>");
         sb.append("<td>");
         sb.append(String.format("%.04f", v.standardDeviation()));
         sb.append("</td>");
         sb.append("<td");
         if (key.equals(maxCategory)) {
            sb.append(" style=\"color: red\"");
         }

         sb.append(">");
         sb.append(String.format("%.01f", total == 0 ? 0.0F : 100.0F * (float)v.frequency / (float)total));
         sb.append("</td>");
         sb.append("</tr>");
      }

   }

   private void printRows(StringBuffer sb, String[] keys) {
      FrequencyAndTotalScore max = new FrequencyAndTotalScore(0, (double)0.0F);
      String maxCategory = keys[0];
      int maxFrequency = 0;
      int total = 0;

      for(String key : keys) {
         FrequencyAndTotalScore v = (FrequencyAndTotalScore)this.buckets.getOrDefault(key, max);
         total += v.frequency;
         if (v.frequency > maxFrequency) {
            maxCategory = key;
            maxFrequency = v.frequency;
         }
      }

      for(String key : keys) {
         sb.append("<tr><td");
         if (key.equals(maxCategory)) {
            sb.append(" style=\"color: red\"");
         }

         sb.append(">");
         sb.append(key);
         sb.append("</td>");
         FrequencyAndTotalScore v = (FrequencyAndTotalScore)this.buckets.getOrDefault(key, max);
         v.print(sb);
         sb.append("<td");
         if (key.equals(maxCategory)) {
            sb.append(" style=\"color: red\"");
         }

         sb.append(">");
         sb.append(String.format("%.01f", total == 0 ? 0.0F : 100.0F * (float)v.frequency / (float)total));
         sb.append("</td>");
         sb.append("</tr>");
      }

   }

   private String tabTitle() {
      return "CoMaS Gaze: " + this.invigilator.getNameAndID();
   }

   public void configure(Properties properties) {
      super.configure(properties);
      String value = PropertyValue.getValue(this.invigilator, "webcam.face", "recognition");
      if (value == null) {
         value = PropertyValue.getValue(this.invigilator, "webcam.face", "detection", "false");
      }

      this.includingFaceDetection = Utils.isTrueOrYes(value);
      if (this.includingFaceDetection) {
         Logger.log(Level.CONFIG, "Webcam face detection is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam.gaze", "detection", "false");
      this.includingGazeDetection = this.includingFaceDetection && Utils.isTrueOrYes(value);
      if (this.includingGazeDetection) {
         Logger.log(Level.CONFIG, "Webcam gaze detection is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam.gaze", "full_report", "false");
      this.includingGazeEyeReport = Utils.isTrueOrYes(value);
      if (this.includingGazeEyeReport) {
         Logger.log(Level.CONFIG, "Webcam gaze eye report is enabled", "");
      }

      value = PropertyValue.getValue(this.invigilator, "webcam.gaze", "full_data_report", "false");
      this.includingAllGazeData = Utils.isTrueOrYes(value);
      if (this.includingAllGazeData) {
         Logger.log(Level.CONFIG, "Webcam gaze full data report is enabled", "");
      }

      CHART_SCRIPT = SystemWebResources.getLocalResource("charts", DEFAULT_CHART_SCRIPT);
   }

   private String chartScript(String[] keysLeft, String[] keysRight, String canvasID) {
      StringBuffer sb = new StringBuffer();
      sb.append("<script>\n");
      sb.append(this.chartSetup(keysLeft, keysRight));
      sb.append("\n");
      sb.append(this.chartConfig());
      sb.append("\n");
      sb.append("var ctx = document.getElementById('");
      sb.append(canvasID);
      sb.append("').getContext('2d');\n");
      sb.append("const chart = new Chart( ctx, config );\n");
      sb.append("</script>\n");
      return sb.toString();
   }

   private String chartLabels(String[] keysLeft, String[] keysRight) {
      StringBuffer sb = new StringBuffer();
      int index = 0;

      for(int keyL = 0; keyL < keysLeft.length; ++keyL) {
         for(int keyR = 0; keyR < keysRight.length; ++keyR) {
            sb.append("\n");
            sb.append("'");
            sb.append(this.gazeKey(keysLeft[keyL], keysRight[keyR]));
            sb.append("'");
            ++index;
            if (index < keysLeft.length * keysRight.length) {
               sb.append(",");
            }
         }
      }

      return sb.toString();
   }

   private String chartData(String[] keysLeft, String[] keysRight) {
      FrequencyAndTotalScore zero = new FrequencyAndTotalScore(0, (double)0.0F);
      StringBuffer sb = new StringBuffer(256);
      int index = 0;
      int total = 0;

      for(int keyL = 0; keyL < keysLeft.length; ++keyL) {
         for(int keyR = 0; keyR < keysRight.length; ++keyR) {
            total += ((FrequencyAndTotalScore)this.buckets.getOrDefault(this.gazeKey(keysLeft[keyL], keysRight[keyR]), zero)).frequency;
         }
      }

      for(int keyL = 0; keyL < keysLeft.length; ++keyL) {
         for(int keyR = 0; keyR < keysRight.length; ++keyR) {
            FrequencyAndTotalScore value = (FrequencyAndTotalScore)this.buckets.getOrDefault(this.gazeKey(keysLeft[keyL], keysRight[keyR]), zero);
            sb.append(String.format("%.01f", (double)value.frequency * (double)100.0F / (double)total));
            ++index;
            if (index < keysLeft.length * keysRight.length) {
               sb.append(",");
            }
         }
      }

      return sb.toString();
   }

   private String chartSetup(String[] keysLeft, String[] keysRight) {
      StringBuffer sb = new StringBuffer(256);
      sb.append("const data = {\n  labels: [");
      sb.append(this.chartLabels(keysLeft, keysRight));
      sb.append("  ],\n  datasets: [{\n    label: 'Gaze Categories',\n    data: [");
      sb.append(this.chartData(keysLeft, keysRight));
      sb.append("],\n    fill: true,\n    backgroundColor: 'rgba(255, 99, 132, 0.2)',\n    borderColor: 'rgb(255, 99, 132)',\n    pointBackgroundColor: 'rgb(255, 99, 132)',\n    pointBorderColor: '#fff',\n    pointHoverBackgroundColor: '#fff',\n    pointHoverBorderColor: 'rgb(255, 99, 132)'\n  }]\n};");
      return sb.toString();
   }

   private String chartConfig() {
      return "const config = {\n\t\t\t\t  type: 'radar',\n\t\t\t\t  data: data,\n\t\t\t\t  options: {\n\t\t\t\t    elements: {\n\t\t\t\t      line: {\n\t\t\t\t        borderWidth: 3\n\t\t\t\t      }\n\t\t\t\t    }\n\t\t\t\t  },\n\t\t\t\t};\n";
   }

   public class Category {
      int index;
      float score;
      String categoryName;
      String displayName;

      public int getIndex() {
         return this.index;
      }

      public void setIndex(int index) {
         this.index = index;
      }

      public float getScore() {
         return this.score;
      }

      public void setScore(float score) {
         this.score = score;
      }

      public String getCategoryName() {
         return this.categoryName;
      }

      public void setCategoryName(String categoryName) {
         this.categoryName = categoryName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public void setDisplayName(String displayName) {
         this.displayName = displayName;
      }

      public void canonicalize() {
         this.categoryName = this.categoryName.replace("eyeLook", "");
      }

      public String toString() {
         return "categoryName: " + this.categoryName + " score: " + this.score;
      }
   }

   public class FrequencyAndTotalScore {
      public int frequency;
      public double score;
      private ArrayList data;

      FrequencyAndTotalScore() {
         this.data = new ArrayList();
         this.frequency = 0;
         this.score = (double)0.0F;
      }

      FrequencyAndTotalScore(int frequency, double score) {
         this.frequency = frequency;
         this.score = score;
         this.data = new ArrayList();
         this.data.add(score);
      }

      public void add(double score) {
         this.data.add(score);
      }

      public void incr(double score) {
         this.score += score;
         ++this.frequency;
      }

      public void increment(double score) {
         this.incr(score);
         this.add(score);
      }

      public int samples() {
         return this.data.size();
      }

      public double max() {
         if (this.data.size() == 0) {
            return (double)0.0F;
         } else {
            double max = Double.MIN_VALUE;

            for(double value : this.data) {
               if (max < value) {
                  max = value;
               }
            }

            return max;
         }
      }

      public double min() {
         if (this.data.size() == 0) {
            return (double)0.0F;
         } else {
            double min = Double.MAX_VALUE;

            for(double value : this.data) {
               if (min > value) {
                  min = value;
               }
            }

            return min;
         }
      }

      public double mean() {
         if (this.data.size() == 0) {
            return (double)0.0F;
         } else {
            double sum = (double)0.0F;

            for(double value : this.data) {
               sum += value;
            }

            return sum / (double)this.data.size();
         }
      }

      public double variance() {
         if (this.data.size() < 2) {
            return (double)0.0F;
         } else {
            double mean = this.mean();
            double variance = (double)0.0F;

            for(double value : this.data) {
               variance += Math.pow(value - mean, (double)2.0F);
            }

            return variance / (double)(this.data.size() - 1);
         }
      }

      public double standardDeviation() {
         double variance = this.variance();
         return Math.sqrt(variance);
      }

      public Double[] toArray() {
         return (Double[])this.data.toArray(new Double[this.data.size()]);
      }

      public void print(StringBuffer sb) {
         sb.append("<td>");
         sb.append(this.frequency);
         sb.append("</td><td>");
         sb.append(String.format("%.04f", this.frequency == 0 ? (double)0.0F : this.score / (double)this.frequency));
         sb.append("</td>");
      }
   }
}
