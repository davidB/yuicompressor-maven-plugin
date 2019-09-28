package net_alchim31_maven_yuicompressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Apply compression on JS and CSS (using YUI Compressor).
 *
 * @goal compress
 * @phase process-resources
 *
 * @author David Bernard
 * @created 2007-08-28
 * @threadSafe
 */
// @SuppressWarnings("unchecked")
public class YuiCompressorMojo extends MojoSupport {

    /**
     * Read the input file using "encoding".
     *
     * @parameter property="file.encoding" default-value="UTF-8"
     */
    private String encoding;

    /**
     * The output filename suffix.
     *
     * @parameter property="maven.yuicompressor.suffix" default-value="-min"
     */
    private String suffix;

    /**
     * If no "suffix" must be add to output filename (maven's configuration manage empty suffix like default).
     *
     * @parameter property="maven.yuicompressor.nosuffix" default-value="false"
     */
    private boolean nosuffix;

    /**
     * Insert line breaks in output after the specified column number.
     *
     * @parameter property="maven.yuicompressor.linebreakpos" default-value="-1"
     */
    private int linebreakpos;

    /**
     * [js only] No compression
     *
     * @parameter property="maven.yuicompressor.nocompress" default-value="false"
     */
    private boolean nocompress;

    /**
     * [js only] Minify only, do not obfuscate.
     *
     * @parameter property="maven.yuicompressor.nomunge" default-value="false"
     */
    private boolean nomunge;

    /**
     * [js only] Preserve unnecessary semicolons.
     *
     * @parameter property="maven.yuicompressor.preserveAllSemiColons" default-value="false"
     */
    private boolean preserveAllSemiColons;

    /**
     * [js only] disable all micro optimizations.
     *
     * @parameter property="maven.yuicompressor.disableOptimizations" default-value="false"
     */
    private boolean disableOptimizations;

    /**
     * force the compression of every files,
     * else if compressed file already exists and is younger than source file, nothing is done.
     *
     * @parameter property="maven.yuicompressor.force" default-value="false"
     */
    private boolean force;

    /**
     * a list of aggregation/concatenation to do after processing,
     * for example to create big js files that contain several small js files.
     * Aggregation could be done on any type of file (js, css, ...).
     *
     * @parameter
     */
    private Aggregation[] aggregations;

    /**
     * request to create a gzipped version of the yuicompressed/aggregation files.
     *
     * @parameter property="maven.yuicompressor.gzip" default-value="false"
     */
    private boolean gzip;

    /**
     * gzip level
     *
     * @parameter property="maven.yuicompressor.level" default-value="9"
     */
    private int level;

    /**
     * show statistics (compression ratio).
     *
     * @parameter property="maven.yuicompressor.statistics" default-value="true"
     */
    private boolean statistics;

    /**
     * aggregate files before minify
     * @parameter property="maven.yuicompressor.preProcessAggregates" default-value="false"
     */
    private boolean preProcessAggregates;

    /**
     * use the input file as output when the compressed file is larger than the original
     * @parameter property="maven.yuicompressor.useSmallestFile" default-value="true"
     */
    private boolean useSmallestFile;

    private long inSizeTotal_;
    private long outSizeTotal_;

    /**
     * Keep track of updated files for aggregation on incremental builds
     */
    private Set<String> incrementalFiles = null;

    @Override
    protected String[] getDefaultIncludes() throws Exception {
        return new String[]{"**/*.css", "**/*.js"};
    }

    @Override
    public void beforeProcess() throws Exception {
        if (nosuffix) {
            suffix = "";
        }

        if(preProcessAggregates) aggregate();
    }

    @Override
    protected void afterProcess() throws Exception {
        if (statistics && (inSizeTotal_ > 0)) {
            getLog().info(String.format("total input (%db) -> output (%db)[%d%%]", inSizeTotal_, outSizeTotal_, ((outSizeTotal_ * 100)/inSizeTotal_)));
        }

        if(!preProcessAggregates) aggregate();
    }

    private void aggregate() throws Exception {
        if (aggregations != null) {
            Set<File> previouslyIncludedFiles = new HashSet<File>();
            for(Aggregation aggregation : aggregations) {
                getLog().info("generate aggregation : " + aggregation.output);
                Collection<File> aggregatedFiles = aggregation.run(previouslyIncludedFiles,buildContext, incrementalFiles);
                previouslyIncludedFiles.addAll(aggregatedFiles);

                File gzipped = gzipIfRequested(aggregation.output);
                if (statistics) {
                    if (gzipped != null) {
                        getLog().info(String.format("%s (%db) -> %s (%db)[%d%%]", aggregation.output.getName(), aggregation.output.length(), gzipped.getName(), gzipped.length(), ratioOfSize(aggregation.output, gzipped)));
                    } else if (aggregation.output.exists()){
                        getLog().info(String.format("%s (%db)", aggregation.output.getName(), aggregation.output.length()));
                    } else {
                        getLog().warn(String.format("%s not created", aggregation.output.getName()));
                    }
                }
            }
        }
    }

    @Override
    protected void processFile(SourceFile src) throws Exception {
        File inFile = src.toFile();
        getLog().debug("on incremental build only compress if input file has Delta");
        if(buildContext.isIncremental()){
            if(!buildContext.hasDelta(inFile)){
                if (getLog().isInfoEnabled()) {
                    getLog().info("nothing to do, " + inFile + " has no Delta");
                }
            	return;
            }
            if(incrementalFiles == null){
            	incrementalFiles = new HashSet<String>();
            }
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("compress file :" + src.toFile()+ " to " + src.toDestFile(suffix));
        }

        File outFile = src.toDestFile(suffix);
        if (isMinifiedFile(inFile)) {
            return;
        }
        if (minifiedFileExistsInSource(inFile, outFile)) {
            getLog().info("compressed file " + outFile.getAbsolutePath() + " already exists in the source directory: " + inFile.getAbsolutePath());
            return;
        }
        getLog().debug("only compress if input file is younger than existing output file");
        if (!force && outFile.exists() && (outFile.lastModified() > inFile.lastModified())) {
            if (getLog().isInfoEnabled()) {
                getLog().info("nothing to do, " + outFile + " is younger than original, use 'force' option or clean your target");
            }
            return;
        }
        InputStreamReader in = null;
        OutputStreamWriter out = null;
        File outFileTmp = new File(outFile.getAbsolutePath() + ".tmp");
        FileUtils.forceDelete(outFileTmp);
        try {
            in = new InputStreamReader(new FileInputStream(inFile), encoding);
            if (!outFile.getParentFile().exists() && !outFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException( "Cannot create resource output directory: " + outFile.getParentFile() );
            }
            getLog().debug("use a temporary outputfile (in case in == out)");

            getLog().debug("start compression");
            /* outFileTmp will be deleted create with FileOutputStream  */
            out = new OutputStreamWriter(new FileOutputStream(outFileTmp), encoding);
            if (nocompress) {
                getLog().info("No compression is enabled");
                IOUtil.copy(in, out);
            } else if (".js".equalsIgnoreCase(src.getExtension())) {
                JavaScriptCompressor compressor = new JavaScriptCompressor(in, jsErrorReporter_);
                compressor.compress(out, linebreakpos, !nomunge, jswarn, preserveAllSemiColons, disableOptimizations);
            } else if (".css".equalsIgnoreCase(src.getExtension())) {
                compressCss(in, out);
            }
            getLog().debug("end compression");
        } finally {
            IOUtil.close(in);
            IOUtil.close(out);
        }

        boolean outputIgnored = useSmallestFile && inFile.length() < outFile.length();
        if (outputIgnored) {
            FileUtils.forceDelete(outFileTmp);
            FileUtils.copyFile(inFile, outFile);
            getLog().debug("output greater than input, using original instead");
        } else {
            FileUtils.forceDelete(outFile);
            FileUtils.rename(outFileTmp, outFile);
            buildContext.refresh(outFile);
        }

        if(buildContext.isIncremental()){
            incrementalFiles.add(outFile.getAbsolutePath());
        }

        File gzipped = gzipIfRequested(outFile);
        if (statistics) {
            inSizeTotal_ += inFile.length();
            outSizeTotal_ += outFile.length();

            String fileStatistics;
            if (outputIgnored) {
                fileStatistics = String.format("%s (%db) -> %s (%db)[compressed output discarded (exceeded input size)]", inFile.getName(), inFile.length(), outFile.getName(), outFile.length());
            } else {
                fileStatistics = String.format("%s (%db) -> %s (%db)[%d%%]", inFile.getName(), inFile.length(), outFile.getName(), outFile.length(), ratioOfSize(inFile, outFile));
            }

            if (gzipped != null) {
                fileStatistics = fileStatistics + String.format(" -> %s (%db)[%d%%]", gzipped.getName(), gzipped.length(), ratioOfSize(inFile, gzipped));
            }
            getLog().info(fileStatistics);
        }
    }

    private void compressCss(InputStreamReader in, OutputStreamWriter out)
            throws IOException {
        try{
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, linebreakpos);
        }catch(IllegalArgumentException e){
            throw new IllegalArgumentException(
                    "Unexpected characters found in CSS file. Ensure that the CSS file does not contain '$', and try again",e);
        }
    }

    protected File gzipIfRequested(File file) throws Exception {
        if (!gzip || (file == null) || (!file.exists())) {
            return null;
        }
        if (".gz".equalsIgnoreCase(FileUtils.getExtension(file.getName()))) {
            return null;
        }
        File gzipped = new File(file.getAbsolutePath()+".gz");
        getLog().debug(String.format("create gzip version : %s", gzipped.getName()));
        GZIPOutputStream out = null;
        FileInputStream in = null;
        try {
            out = new GZIPOutputStream(buildContext.newFileOutputStream(gzipped)){
                {
                    def.setLevel(level);
                }
            };
            in = new FileInputStream(file);
            IOUtil.copy(in, out);
        } finally {
            IOUtil.close(in);
            IOUtil.close(out);
        }
        return gzipped;
    }

    protected long ratioOfSize(File file100, File fileX) throws Exception {
        long v100 = Math.max(file100.length(), 1);
        long vX = Math.max(fileX.length(), 1);
        return (vX * 100)/v100;
    }

    private boolean isMinifiedFile(File inFile) {
        String filename = inFile.getName().toLowerCase();
        if (filename.endsWith(suffix + ".js") || filename.endsWith(suffix + ".css")) {
            return true;
        }
        return false;
    }

    private static boolean minifiedFileExistsInSource(File source, File dest) throws InterruptedException {
        String parent = source.getParent();
        String destFilename = dest.getName();
        File file = new File(parent + File.separator + destFilename);
        if (file.exists()) {
            return true;
        }
        return false;
    }
}
