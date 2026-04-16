package edu.carleton.cas.resources;

import java.io.File;

public interface FileProcessor extends OutputProcessor {
   File getInputFile();

   File getOutputFile();
}
