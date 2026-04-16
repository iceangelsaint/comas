package edu.carleton.cas.resources;

import edu.carleton.cas.constants.ClientShared;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SystemWebResources {
   private static final String prefix = "system.resources.";
   private static String faceDetector = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3";
   private static String tesseractWorkerPath = "https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/worker.min.js";
   private static String stylesheet = "<link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\" />";
   private static String fonts = "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css\" integrity=\"sha512-SfTiTlX6kk+qitfevl/7LibUOeJWlt9rbyDn92a1DqWOw9vWG2MFoays0sgObmWazO5BQPiFucnnEAjpAB+/Sw==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\" />";
   private static String faceAPI = "<script src=\"%s://%s:%s/CMS/rest/js/face-apis/dist/face-api.js\"></script>";
   private static String tesseract = "<script src=\"https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js\"></script>";
   private static String webcam = "<script type=\"text/javascript\" src=\"%s://%s:%s/CMS/rest/js/webcam-easy/dist/webcam-easy.js\"></script>";
   private static String icon = "<link rel=\"icon\" type=\"image/png\" href=\"%s://%s:%s/CMS/rest/images/social-sharing-icon-16x16.png\" />";
   private static String appImage = "/images/social-sharing-icon-96x96.png";
   private static String serverAppImage = "<img alt=\"CoMaS logo\" src=\"%s://%s:%s/CMS/rest/images/social-sharing-icon-96x96.png\" />";
   private static String idPattern = "^10[\\d]{7}$";
   private static String namePattern = "^[a-zA-Z\\.][\\sa-zA-Z'-_]{1,32}$";
   private static String passcodePattern = "^[\\da-zA-Z]{6}$";
   private static String entityNamePattern = "^[a-zA-Z\\d-]{1,32}$";
   private static String emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
   private static String lcs = "function longestCommonSubstring(str1, str2) {\n    // Initialize a matrix to store the length of the common substrings\n    const matrix = Array(str1.length + 1).fill(0).map(() => Array(str2.length + 1).fill(0));\n    \n    // Variables to keep track of the length of the longest common substring and its ending position\n    let maxLength = 0;\n    let endIndex = 0;\n\n    // Loop through both strings\n    for (let i = 1; i <= str1.length; i++) {\n        for (let j = 1; j <= str2.length; j++) {\n            if (str1[i - 1] === str2[j - 1]) {\n                matrix[i][j] = matrix[i - 1][j - 1] + 1;\n                if (matrix[i][j] > maxLength) {\n                    maxLength = matrix[i][j];\n                    endIndex = i - 1;\n                }\n            } else {\n                matrix[i][j] = 0;\n            }\n        }\n    }\n\n    // Extract the longest common substring\n    const commonSubstring = str1.substring(endIndex - maxLength + 1, endIndex + 1);\n\n    return commonSubstring;\n}";
   private static String os = "function getOperatingSystem() {\n    let userAgent = navigator.userAgent || navigator.vendor || window.opera;\n    if (/windows phone/i.test(userAgent)) {\n        return \"Windows Phone\";\n    }\n    if (/win/i.test(userAgent)) {\n        return \"Windows\";\n    }\n    if (/android/i.test(userAgent)) {\n        return \"Android\";\n    }\n    if (/iPad|iPhone|iPod/.test(userAgent) && !window.MSStream) {\n        return \"iOS\";\n    }\n    if (/Mac/i.test(userAgent)) {\n        return \"MacOS\";\n    }\n    if (/linux/i.test(userAgent)) {\n        return \"Linux\";\n    }\n    return \"unknown\";\n}\n";
   private static String quitOnClose = "   window.onbeforeunload = function(event) {\n      event.preventDefault();\n      var confirmationMessage = 'Are you sure you want to quit?';\n      event.returnValue = confirmationMessage;\n      return confirmationMessage;\n   };\n";
   private static String searchQuery = "https://www.google.com/search?q=";
   private static ConcurrentHashMap resources = new ConcurrentHashMap();

   static {
      resources.put(getVariable("stylesheet"), stylesheet);
      resources.put(getVariable("fonts"), fonts);
      resources.put(getVariable("faceAPI"), faceAPI);
      resources.put(getVariable("webcam"), webcam);
      resources.put(getVariable("tesseract"), tesseract);
      resources.put(getVariable("id"), idPattern);
      resources.put(getVariable("name"), namePattern);
      resources.put(getVariable("passcode"), passcodePattern);
      resources.put(getVariable("entity"), entityNamePattern);
      resources.put(getVariable("icon"), icon);
      resources.put(getVariable("appImage"), appImage);
      resources.put(getVariable("serverAppImage"), serverAppImage);
      resources.put(getVariable("lcs"), lcs);
      resources.put(getVariable("os"), os);
      resources.put(getVariable("email"), emailPattern);
      resources.put(getVariable("tesseractWorkerPath"), tesseractWorkerPath);
      resources.put(getVariable("faceDetector"), faceDetector);
      resources.put(getVariable("searchQuery"), searchQuery);
   }

   public static void configure(Properties properties) {
      if (properties != null) {
         properties.forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith("system.resources.")) {
               resources.put(key, v.toString());
            }

         });
      }
   }

   public static String getVariable(String variable) {
      return "system.resources." + variable;
   }

   public static String getResource(String key, String defaultValue) {
      String value = getResource(key);
      return value == null ? defaultValue : value;
   }

   public static String getLocalResource(String key) {
      return getResource(getVariable(key));
   }

   public static String getLocalResource(String key, String value) {
      return getResource(getVariable(key), value);
   }

   public static void setLocalResource(String key, String value) {
      setResource(getVariable(key), value);
   }

   public static String getResource(String key) {
      return key == null ? null : (String)resources.get(key);
   }

   public static void setResource(String key, String value) {
      if (key != null && value != null) {
         resources.put(key, value);
      }
   }

   public static String removeResource(String key) {
      return (String)resources.remove(key);
   }

   public static String getStylesheet(String otherUsingCMSHost) {
      return String.format(getResource(getVariable(otherUsingCMSHost), stylesheet), ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT);
   }

   public static String getStylesheet() {
      return getResource(getVariable("stylesheet"), stylesheet);
   }

   public static String getFonts() {
      return getResource(getVariable("fonts"), fonts);
   }

   public static String sorting(String id) {
      StringBuffer sb = new StringBuffer("const getCellValue");
      sb.append(id);
      sb.append("= (tr, idx) => tr.children[idx].innerText || tr.children[idx].textContent;");
      sb.append("const comparer");
      sb.append(id);
      sb.append(" = (idx, asc) => (a, b) => ((v1, v2) => v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2) ? v1 - v2 : v1.toString().localeCompare(v2))(getCellValue");
      sb.append(id);
      sb.append("(asc ? a : b, idx), getCellValue");
      sb.append(id);
      sb.append("(asc ? b : a, idx));document.querySelectorAll('th#");
      sb.append(id);
      sb.append("').forEach(th => th.addEventListener('click', (() => {const table = th.closest('table');Array.from(table.querySelectorAll('tr:nth-child(n+2)')).sort(comparer");
      sb.append(id);
      sb.append("(Array.from(th.parentNode.children).indexOf(th), this.asc = !this.asc)).forEach(tr => table.appendChild(tr) );})));");
      return sb.toString();
   }

   public static String getTesseractWorkerPath() {
      return getResource(getVariable("tesseractWorkerPath"), tesseractWorkerPath);
   }

   public static String getWebcam() {
      return String.format(getResource(getVariable("webcam"), webcam), ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT);
   }

   public static String getTesseract() {
      return getResource(getVariable("tesseract"), tesseract);
   }

   public static String getNamePattern() {
      return getResource(getVariable("name"), namePattern);
   }

   public static String getIDPattern() {
      return getResource(getVariable("id"), idPattern);
   }

   public static String getPasscodePattern() {
      return getResource(getVariable("passcode"), passcodePattern);
   }

   public static String getEntityPattern() {
      return getResource(getVariable("entity"), entityNamePattern);
   }

   public static String getIcon() {
      return String.format(getResource(getVariable("icon"), icon), ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT);
   }

   public static String getServerAppImage() {
      return String.format(getResource(getVariable("serverAppImage"), serverAppImage), ClientShared.PROTOCOL, ClientShared.CMS_HOST, ClientShared.PORT);
   }

   public static String getAppImage() {
      return getResource(getVariable("appImage"), appImage);
   }

   public static String getHomePage() {
      return getLocalResource("runningLandingPage", "/home");
   }

   public static String getSearchQuery() {
      return getLocalResource("searchQuery", searchQuery);
   }

   public static String getEmailPattern() {
      return getResource(getVariable("email"), emailPattern);
   }

   public static String getHomeButton(String spacer) {
      String var10000 = getHomePage();
      String homeButton = "<button accesskey=\"h\" class=\"w3-button w3-round-large w3-blue\" onclick='goTo(\"" + var10000 + "\", \"Home\");'>Home</button>" + spacer;
      return homeButton;
   }

   public static String getHomeButton() {
      return getHomeButton("&nbsp;&nbsp;&nbsp;&nbsp;");
   }

   public static String getLCS() {
      return getResource(getVariable("lcs"), lcs);
   }

   public static String getAlertWithTimeout() {
      return "function alertWithTimeout(msg, duration) {\n   let field = document.getElementById('msg');\n   if (field !== null)\n      field.innerText = msg;\n   if (typeof alertDialog !== 'undefined') {\n      alertDialog.showModal();\n      setTimeout(function() {\n            alertDialog.close();\n      }, duration);\n   };\n}\n";
   }

   public static String getQuitOnClose() {
      return quitOnClose;
   }

   public static String htmlPage(String bodyHTMLAfterImage) {
      return htmlPage("CoMaS", bodyHTMLAfterImage);
   }

   public static String htmlPage(String title, String bodyHTMLAfterImage) {
      return String.format("<html><head><title>%s</title>%s%s</head><body><div class=\"w3-container w3-center\"><br/><img alt=\"CoMaS logo\" src=\"%s\">%s</div></body></html>", title, getStylesheet(), getIcon(), getAppImage(), bodyHTMLAfterImage);
   }

   public static String htmlPage(String title, String header, String bodyHTML) {
      return String.format("<html><head><title>%s</title>%s%s</head><body><div class=\"w3-container w3-center\"><br/>%s%s</div></body></html>", title, getStylesheet(), getIcon(), header, bodyHTML);
   }
}
