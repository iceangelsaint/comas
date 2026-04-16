package edu.carleton.cas.jetty.embedded.processors;

import edu.carleton.cas.file.Utils;
import edu.carleton.cas.jetty.embedded.ServletProcessor;
import edu.carleton.cas.resources.SystemWebResources;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import javax.servlet.http.HttpServletResponse;

public class TextRenderer extends AbstractRenderer implements RendererInterface {
   public TextRenderer(File file, ServletProcessor servletProcessor) {
      super(file, servletProcessor);
   }

   public String getMimeType() {
      return "text/html";
   }

   public void render(HttpServletResponse response) throws IOException {
      PrintWriter wr = response.getWriter();
      wr.print("<html lang=\"en\" xml:lang=\"en\"><head><meta charset=\"UTF-8\"><title>CoMaS ");
      wr.print(this.file.getName());
      wr.print(" for ");
      wr.print(this.servletProcessor.getInvigilator().getCourse());
      wr.print("/");
      wr.print(this.servletProcessor.getInvigilator().getActivity());
      wr.println("</title>");
      wr.println(SystemWebResources.getStylesheet());
      wr.println(SystemWebResources.getIcon());
      wr.println("</head><body><div class=\"w3-container w3-center\"><div class=\"w3-panel\">");
      wr.println(this.servletProcessor.checkForServletCode(0, false, (String)null, ""));
      wr.print("<input type=\"hidden\" id=\"QUESTION_FILE\" value=\"");
      wr.print(this.file.getName());
      wr.println("\"></input>");
      wr.print("<input class=\"w3-center w3-bold w3-black\" type=\"text\" readonly id=\"QUESTION_NAME\" name=\"QUESTION\" value=\"");
      String name = this.servletProcessor.getInvigilator().getExamQuestionProperties().getLabel(this.file.getName(), this.file.getName());
      wr.print(Utils.removeExtension(name));
      wr.println("\"></input>");
      wr.println("<p id=\"time\"></p>");
      wr.println("<textarea id=\"answer\" wrap=\"hard\" rows=\"20\" cols=\"80\">");
      byte[] bytes = Files.readAllBytes(this.file.toPath());
      wr.print(new String(bytes));
      wr.println("</textarea>");
      wr.println("<script language=\"javascript\">");
      wr.println("\tvar timerId;");
      wr.println("    function save() {");
      wr.println("\t   var indexOfEdge = navigator.userAgent.indexOf(\"Edge\");");
      wr.println("\t   var indexOfMSIE = navigator.userAgent.indexOf(\"Trident\");");
      wr.println("       if (indexOfEdge != -1 || indexOfMSIE != -1) {");
      wr.println("          alert(\"Unsupported browser detected. Please use Chrome, Edge or Safari\");");
      wr.println("          window.location.assign(\"https://www.google.com/chrome/\");");
      wr.println("       } else {");
      wr.println("    var erf = function() {\n        if (typeof alertWithTimeout === 'function')\n            alertWithTimeout(\"Your session has now ended\", 5000);\n        else\n            alert(\"Your session has now ended\");\n        document.getElementById(\"saveButton\").disabled = true;\n        document.getElementById(\"questionsButton\").disabled = true;\n        document.getElementById(\"homeButton\").disabled = true;\n        document.getElementById(\"quitButton\").disabled = true;\n        document.title = \"CoMaS Session Ended\";\n        }");
      wr.println("          var xhttp = new XMLHttpRequest();");
      wr.println("          const formData = new FormData();");
      wr.println("          var txt = document.getElementById(\"answer\").value;");
      wr.println("          formData.append(\"answer\", txt);");
      wr.println("          formData.append(\"file\", \"" + this.file.getName() + "\");");
      wr.println("          formData.append(\"QUESTION_FILE\", \"" + this.file.getName() + "\");");
      wr.println("          xhttp.onerror = erf;\n");
      wr.println("          xhttp.ontimeout = erf;\n");
      wr.println("          xhttp.onabort = erf;\n");
      wr.println("          xhttp.onreadystatechange = function() {");
      wr.println("        \t if (this.readyState == XMLHttpRequest.DONE) {");
      wr.println("        \t\tvar time = document.getElementById(\"time\");");
      wr.println("                if (this.status == 200) {");
      wr.println("                   time.innerHTML = '<span class=\"w3-tag w3-blue\">Saved: '+new Date().toLocaleString() +'</span>';");
      wr.print("                   setCookieValue(\"");
      wr.print(this.servletProcessor.getInvigilator().getCourse());
      wr.print("-");
      wr.print(this.servletProcessor.getInvigilator().getActivity());
      wr.print("-");
      wr.print(this.file.getName());
      wr.println("\", Date.now(), 1);");
      wr.println("                } else {");
      wr.println("                   time.innerHTML = '<span class=\"w3-tag w3-red\">Save Failed: '+new Date().toLocaleString() +'</span>';");
      wr.println("                }");
      wr.println("        \t }");
      wr.println("          };");
      wr.print("          xhttp.open(\"POST\", \"");
      wr.print("/");
      wr.print(this.file.getName());
      wr.println("\");");
      wr.println("          xhttp.send(formData);");
      wr.println("      }");
      wr.println("   }");
      wr.println("   function autosave() {");
      wr.println("      save();");
      wr.println("      timerId = setTimeout(autosave, 300000);");
      wr.println("   }");
      wr.println("   function cancelAutosave() {");
      wr.println("      if (timerId) {");
      wr.println("         clearTimeout(timerId);");
      wr.println("         timerId = 0;");
      wr.println("      }");
      wr.println("   };");
      wr.println("   timerId = setTimeout(autosave, 300000);");
      wr.println("   var indexOfEdge = navigator.userAgent.indexOf(\"Edge\");");
      wr.println("   var indexOfMSIE = navigator.userAgent.indexOf(\"Trident\");");
      wr.println("   if (indexOfEdge != -1 || indexOfMSIE != -1) {");
      wr.println("      alert(\"Unsupported browser detected. Please use Chrome, Edge or Safari\");");
      wr.println("      window.location.assign(\"https://www.google.com/chrome/\");");
      wr.println("   }");
      wr.println("</script>");
      wr.println("<br/><input accesskey=\"x\" type=\"button\" class=\"w3-button w3-blue w3-round-large w3-margin\" id=\"questionsButton\" value=\"Exam\" onclick=\"window.location.href='/questions';\"></input>\n");
      wr.println("<input accesskey=\"s\" type=\"button\" class=\"w3-button w3-green w3-round-large w3-margin\" id=\"saveButton\" value=\"Save\" onclick=\"save();\"></input>");
      wr.println("<input accesskey=\"h\" type=\"button\" class=\"w3-button w3-blue w3-round-large w3-margin\" id=\"homeButton\" value=\"Home\" onclick=\"window.location.href='/exam';\"></input>\n");
      wr.println("<input accesskey=\"q\" type=\"button\" class=\"w3-button w3-blue w3-round-large w3-margin\" id=\"quitButton\" value=\"Quit\" onclick=\"window.location.href='/quit';\"></input>\n");
      wr.println(this.servletProcessor.footerForServlet());
      wr.println("</div></div></body></html>");
   }
}
