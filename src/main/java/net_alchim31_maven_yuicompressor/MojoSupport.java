package net_alchim31_maven_yuicompressor;

import java.io.File;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Common class for mojos.
 *
 * @author David Bernard
 * @created 2007-08-29
 */
// @SuppressWarnings("unchecked")
public abstract class MojoSupport extends AbstractMojo {
    private static final String[] EMPTY_STRING_ARRAY = {};

    /**
     * Javascript source directory. (result will be put to outputDirectory).
     * This allow project with "src/main/js" structure.
     *
     * @parameter expression="${project.build.sourceDirectory}/../js"
     */
    private File sourceDirectory;

    /**
     * Single directory for extra files to include in the WAR.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     */
    private File warSourceDirectory;

    /**
     * The directory where the webapp is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     */
    private File webappDirectory;

    /**
     * The output directory into which to copy the resources.
     *
     * @parameter expression="${project.build.outputDirectory}"
     */
    private File outputDirectory;

    /**
     * The list of resources we want to transfer.
     *
     * @parameter expression="${project.resources}"
     */
    private List<Resource> resources;

    /**
     * list of additionnal excludes
     *
     * @parameter
     */
    private List<String> excludes;

    /**
     * Excludes files from resources directories.
     *
     * @parameter default-value="false"
     */
    private boolean excludeResources = false;

    /**
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    protected MavenProject project;

    /**
     * [js only] Display possible errors in the code
     *
     * @parameter expression="${maven.yuicompressor.jswarn}" default-value="true"
     */
    protected boolean jswarn;

    /**
     * Whether to skip execution.
     *
     * @parameter expression="${maven.yuicompressor.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * define if plugin must stop/fail on warnings.
     *
     * @parameter expression="${maven.yuicompressor.failOnWarning}" default-value="false"
     */
    protected boolean failOnWarning;
    protected ErrorReporter4Mojo jsErrorReporter_;

    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (skip) {
                getLog().debug("run of yuicompressor-maven-plugin skipped");
                return;
            }
            if (failOnWarning) {
                jswarn = true;
            }
            jsErrorReporter_ = new ErrorReporter4Mojo(getLog(), jswarn);
            beforeProcess();
            processDir(sourceDirectory, outputDirectory, null, true);
            if (!excludeResources) {
              for (Resource resource : resources){
                  File destRoot = outputDirectory;
                  if (resource.getTargetPath() != null) {
                      destRoot = new File(outputDirectory, resource.getTargetPath());
                  }
                  processDir(new File( resource.getDirectory() ), destRoot, resource.getExcludes(), true);
              }
            }
            processDir(warSourceDirectory, webappDirectory, null, false);
            afterProcess();
            getLog().info(String.format("nb warnings: %d, nb errors: %d", jsErrorReporter_.getWarningCnt(), jsErrorReporter_.getErrorCnt()));
            if (failOnWarning && (jsErrorReporter_.getWarningCnt() > 0)) {
                throw new MojoFailureException("warnings on "+ this.getClass().getSimpleName() + "=> failure ! (see log)");
            }
        } catch (RuntimeException exc) {
            throw exc;
        } catch (MojoFailureException exc) {
            throw exc;
        } catch (MojoExecutionException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new MojoExecutionException("wrap: " + exc.getMessage(), exc);
        }
    }

    protected abstract String[] getDefaultIncludes() throws Exception;
    protected abstract void beforeProcess() throws Exception;
    protected abstract void afterProcess() throws Exception;

    /**
     * Force to use defaultIncludes (ignore srcIncludes) to avoid processing resources/includes from other type than *.css or *.js
     * @see https://github.com/davidB/yuicompressor-maven-plugin/issues/19 
     */
    protected void processDir(File srcRoot, File destRoot, List<String> srcExcludes, boolean destAsSource) throws Exception {
        if ((srcRoot == null) || ( !srcRoot.exists() )) {
            return;
        }
        if (destRoot == null) {
            throw new MojoFailureException("destination directory for " + srcRoot + " is null");
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(srcRoot);
        scanner.setIncludes(getDefaultIncludes());
        if ( (srcExcludes != null) && !srcExcludes.isEmpty() ) {
            scanner.setExcludes( srcExcludes.toArray( EMPTY_STRING_ARRAY ) );
        }
        if ((excludes != null) && !excludes.isEmpty()) {
            scanner.setExcludes( excludes.toArray( EMPTY_STRING_ARRAY ) );
        }
        scanner.addDefaultExcludes();
        scanner.scan();
        for(String name :scanner.getIncludedFiles() ) {
            SourceFile src = new SourceFile(srcRoot, destRoot, name, destAsSource);
            jsErrorReporter_.setDefaultFileName("..." + src.toFile().getAbsolutePath().substring(project.getBasedir().getAbsolutePath().length()));
            processFile(src);
        }
    }

    protected abstract void processFile(SourceFile src) throws Exception;
}
