package net_alchim31_maven_googleclosure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.logging.Level;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

import net_alchim31_maven_yuicompressor.SourceFile;
import net_alchim31_maven_yuicompressor.YuiCompressorMojo;

/**
 * Apply compression on JS and CSS (using YUI Compressor).
 *
 * @goal compress
 * @phase process-resources
 *
 * @author Rishikesh Darandale
 * @created 2013-05-05
 * @threadSafe
 */
public class GoogleClosureMojo extends YuiCompressorMojo {
	 /**
     * The compiler to be used.
     *
     * @parameter expression="${maven.yuicompressor.compiler}" default-value="yui"
     */
    private String compiler;
    
    @Override
    protected void processFile(SourceFile src) throws Exception {
        if (getLog().isDebugEnabled()) {
            getLog().debug("compress file :" + src.toFile()+ " to " + src.toDestFile( this.getSuffix() ));
        }
        File inFile = src.toFile();
        File outFile = src.toDestFile( this.getSuffix() );

        getLog().debug("only compress if input file is younger than existing output file");
        if (!isForce() && outFile.exists() && (outFile.lastModified() > inFile.lastModified())) {
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
            in = new InputStreamReader(new FileInputStream(inFile), this.getEncoding() );
            if (!outFile.getParentFile().exists() && !outFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException( "Cannot create resource output directory: " + outFile.getParentFile() );
            }
            getLog().debug("use a temporary outputfile (in case in == out)");

            getLog().debug("start compression");
            out = new OutputStreamWriter(new FileOutputStream(outFileTmp), this.getEncoding() );
            if ( this.isNocompress() ) {
                getLog().info("No compression is enabled");
                IOUtil.copy(in, out);
            }
            else if (".js".equalsIgnoreCase(src.getExtension())) {
            	if (getLog().isDebugEnabled()) {
                    getLog().debug("Compiler selected: " + compiler );
                }
            	if( compiler == null || "".equalsIgnoreCase( compiler ) 
            			|| compiler.equalsIgnoreCase( "yui" ) ) {
            		compressUsingYUI(in, out);
            	}else if ( compiler.equalsIgnoreCase("closure") ) {
            		compressUsingClosure( inFile, out );
            	}               
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
        if (isStatistics()) {
            inSizeTotal_ += inFile.length();
            outSizeTotal_ += outFile.length();
            getLog().info(String.format("%s (%db) -> %s (%db)[%d%%]", inFile.getName(), inFile.length(), outFile.getName(), outFile.length(), ratioOfSize(inFile, outFile)));
            if (gzipped != null) {
                getLog().info(String.format("%s (%db) -> %s (%db)[%d%%]", inFile.getName(), inFile.length(), gzipped.getName(), gzipped.length(), ratioOfSize(inFile, gzipped)));
            }
        }
    }
    
    protected void compressUsingYUI(InputStreamReader in, OutputStreamWriter out) throws IOException{
    	 JavaScriptCompressor compressor = new JavaScriptCompressor(in, jsErrorReporter_);
         compressor.compress(out, getLinebreakpos(), !isNomunge(), jswarn, isPreserveAllSemiColons(), isDisableOptimizations());
    }
    
    protected void compressUsingClosure( File inFile, OutputStreamWriter out ){
    	Compiler.setLoggingLevel(Level.INFO);
    	Compiler compiler = new Compiler();
    	com.google.javascript.jscomp.SourceFile input = com.google.javascript.jscomp.SourceFile.fromFile( inFile );    	
		// create the options for the google closure
    	CompilerOptions options = new CompilerOptions();
		options.setCodingConvention(new ClosureCodingConvention());
		CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
		if ( isDisableOptimizations() )
			level = CompilationLevel.WHITESPACE_ONLY;
	    level.setOptionsForCompilationLevel(options);
	    		
		com.google.javascript.jscomp.SourceFile externs = com.google.javascript.jscomp.SourceFile.fromCode("/dev/null", "");
		compiler.compile( externs , input, options);		
		try {
			out.write( compiler.toSource() );
		} catch (IOException e) {
			this.getLog().error( "Error occurred while writing a file..." + e );
		}
		// Get the errors/warnings related to this compilation and add it to errorRepoarter
		if( compiler.getErrorCount() > 0 ) {
			for( JSError error: compiler.getErrors() ){
				jsErrorReporter_.error(error.description, error.sourceName, error.getLineNumber(), error.sourceName, error.getNodeSourceOffset());
			}
		}
		if( compiler.getWarningCount() > 0 ) {
			for( JSError error: compiler.getWarnings() ){
				jsErrorReporter_.warning(error.description, error.sourceName, error.getLineNumber(), error.sourceName, error.getNodeSourceOffset());
			}
		}
    }    
}
