package net_alchim31_maven_yuicompressor;

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.*;

public class Aggregation {
    public File inputDir;
    public File output;
    public String[] includes;
    public String[] excludes;
    public boolean removeIncluded = false;
    public boolean insertNewLine = false;
    public boolean insertFileHeader = false;
    public boolean fixLastSemicolon = false;
    public boolean autoExcludeWildcards = false;

    public List<File> run(Collection<File> previouslyIncludedFiles, BuildContext buildContext) throws Exception {
        return this.run(previouslyIncludedFiles, buildContext, null);
    }

    public List<File> run(Collection<File> previouslyIncludedFiles, BuildContext buildContext, Set<String> incrementalFiles) throws Exception {
        defineInputDir();

        List<File> files;
        if (autoExcludeWildcards) {
            files = getIncludedFiles(previouslyIncludedFiles, buildContext, incrementalFiles);
        } else {
            files = getIncludedFiles(null, buildContext, incrementalFiles);
        }

        if (files.size() != 0) {
            output = output.getCanonicalFile();
            output.getParentFile().mkdirs();
            OutputStream out = buildContext.newFileOutputStream(output);
            try {
                for (File file : files) {
                    if (file.getCanonicalPath().equals(output.getCanonicalPath())) {
                        continue;
                    }
                    FileInputStream in = new FileInputStream(file);
                    try {
                        if (insertFileHeader) {
                            out.write(createFileHeader(file).getBytes());
                        }
                        IOUtil.copy(in, out);
                        if (fixLastSemicolon) {
                            out.write(';');
                        }
                        if (insertNewLine) {
                            out.write('\n');
                        }
                    } finally {
                        IOUtil.close(in);
                        in = null;
                    }
                    if (removeIncluded) {
                        file.delete();
                        buildContext.refresh(file);
                    }
                }
            } finally {
                IOUtil.close(out);
                out = null;
            }
        }
        return files;
    }

    private String createFileHeader(File file) {
        StringBuilder header = new StringBuilder();
        header.append("/*");
        header.append(file.getName());
        header.append("*/");

        if (insertNewLine) {
            header.append('\n');
        }

        return header.toString();
    }

    private void defineInputDir() throws Exception {
        if (inputDir == null) {
            inputDir = output.getParentFile();
        }
        inputDir = inputDir.getCanonicalFile();
        if (!inputDir.isDirectory()) {
            throw new IllegalStateException("input directory not found: " + inputDir);
        }
    }

    private List<File> getIncludedFiles(Collection<File> previouslyIncludedFiles, BuildContext buildContext, Set<String> incrementalFiles) throws Exception {
        List<File> filesToAggregate = new ArrayList<>();
        if (includes != null) {
            for (String include : includes) {
                addInto(include, filesToAggregate, previouslyIncludedFiles);
            }
        }

        //If build is incremental with no delta, then don't include for aggregation
        if (buildContext.isIncremental()) {

            if (incrementalFiles != null) {
                boolean aggregateMustBeUpdated = false;
                for (File file : filesToAggregate) {
                    if (incrementalFiles.contains(file.getAbsolutePath())) {
                        aggregateMustBeUpdated = true;
                        break;
                    }
                }

                if (aggregateMustBeUpdated) {
                    return filesToAggregate;
                }
            }
            return new ArrayList<File>();
        } else {
            return filesToAggregate;
        }

    }

    private void addInto(String include, List<File> includedFiles, Collection<File> previouslyIncludedFiles) throws Exception {
        if (include.indexOf('*') > -1) {
            DirectoryScanner scanner = newScanner();
            scanner.setIncludes(new String[]{include});
            scanner.scan();
            String[] rpaths = scanner.getIncludedFiles();
            Arrays.sort(rpaths);
            for (String rpath : rpaths) {
                File file = new File(scanner.getBasedir(), rpath);
                if (!includedFiles.contains(file) && (previouslyIncludedFiles == null || !previouslyIncludedFiles.contains(file))) {
                    includedFiles.add(file);
                }
            }
        } else {
            File file = new File(include);
            if (!file.isAbsolute()) {
                file = new File(inputDir, include);
            }
            if (!includedFiles.contains(file)) {
                includedFiles.add(file);
            }
        }
    }

    private DirectoryScanner newScanner() throws Exception {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(inputDir);
        if ((excludes != null) && (excludes.length != 0)) {
            scanner.setExcludes(excludes);
        }
        scanner.addDefaultExcludes();
        return scanner;
    }
}
