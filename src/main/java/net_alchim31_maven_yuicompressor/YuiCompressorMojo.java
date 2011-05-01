package net_alchim31_maven_yuicompressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
 */
// @SuppressWarnings("unchecked")
public class YuiCompressorMojo extends MojoSupport {

    /**
     * Read the input file using "encoding".
     *
     * @parameter expression="${file.encoding}" default-value="UTF-8"
     */
    private String encoding;

    /**
     * The output filename suffix.
     *
     * @parameter expression="${maven.yuicompressor.suffix}" default-value="-min"
     */
    private String suffix;

    /**
     * If no "suffix" must be add to output filename (maven's configuration manage empty suffix like default).
     *
     * @parameter expression="${maven.yuicompressor.nosuffix}" default-value="false"
     */
    private boolean nosuffix;

    /**
     * Insert line breaks in output after the specified column number.
     *
     * @parameter expression="${maven.yuicompressor.linebreakpos}" default-value="0"
     */
    private int linebreakpos;

    /**
     * [js only] Minify only, do not obfuscate.
     *
     * @parameter expression="${maven.yuicompressor.nomunge}" default-value="false"
     */
    private boolean nomunge;

    /**
     * [js only] Preserve unnecessary semicolons.
     *
     * @parameter expression="${maven.yuicompressor.preserveAllSemiColons}" default-value="false"
     */
    private boolean preserveAllSemiColons;

    /**
     * [js only] disable all micro optimizations.
     *
     * @parameter expression="${maven.yuicompressor.disableOptimizations}" default-value="false"
     */
    private boolean disableOptimizations;

    /**
     * force the compression of every files,
     * else if compressed file already exists and is younger than source file, nothing is done.
     *
     * @parameter expression="${maven.yuicompressor.force}" default-value="false"
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
     * @parameter expression="${maven.yuicompressor.gzip}" default-value="false"
     */
    private boolean gzip;

    /**
     * show statistics (compression ratio).
     *
     * @parameter expression="${maven.yuicompressor.statistics}" default-value="true"
     */
    private boolean statistics;

    /**
     * aggregate files before minify
     * @parameter expression="${maven.yuicompressor.preProcessAggregates}" default-value="false"
     */
    private boolean preProcessAggregates;

    private long inSizeTotal_;
    private long outSizeTotal_;

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
            for(Aggregation aggregation : aggregations) {
                getLog().info("generate aggregation : " + aggregation.output);
                aggregation.run();
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
        if (getLog().isDebugEnabled()) {
            getLog().debug("compress file :" + src.toFile()+ " to " + src.toDestFile(suffix));
        }
        File inFile = src.toFile();
        File outFile = src.toDestFile(suffix);

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
            out = new OutputStreamWriter(new FileOutputStream(outFileTmp), encoding);
            if (".js".equalsIgnoreCase(src.getExtension())) {
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
        FileUtils.forceDelete(outFile);
        FileUtils.rename(outFileTmp, outFile);
        File gzipped = gzipIfRequested(outFile);
        if (statistics) {
            inSizeTotal_ += inFile.length();
            outSizeTotal_ += outFile.length();
            getLog().info(String.format("%s (%db) -> %s (%db)[%d%%]", inFile.getName(), inFile.length(), outFile.getName(), outFile.length(), ratioOfSize(inFile, outFile)));
            if (gzipped != null) {
                getLog().info(String.format("%s (%db) -> %s (%db)[%d%%]", inFile.getName(), inFile.length(), gzipped.getName(), gzipped.length(), ratioOfSize(inFile, gzipped)));
            }
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
            out = new GZIPOutputStream(new FileOutputStream(gzipped));
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
}
