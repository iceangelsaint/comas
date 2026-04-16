package com.cogerent.detector;

public class DisplayInfo {
   String instance;
   String manufacturer;
   String model;
   String productCode;
   String serial;
   String connectionType;
   byte[] edid;

   public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(this.manufacturer);
      sb.append("-");
      if (this.model != null && !this.model.isEmpty()) {
         sb.append(this.model);
      } else {
         sb.append("?");
      }

      sb.append("-");
      if (this.productCode != null && !this.productCode.isEmpty()) {
         sb.append(this.productCode);
      } else {
         sb.append("?");
      }

      sb.append("-");
      if (this.serial != null && !this.serial.isEmpty()) {
         sb.append(this.serial);
      } else {
         sb.append("?");
      }

      sb.append("-");
      if (this.connectionType != null && !this.connectionType.isEmpty()) {
         sb.append(this.connectionType);
      } else {
         sb.append("?");
      }

      return sb.toString();
   }
}
