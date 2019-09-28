package net_alchim31_maven_yuicompressor;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.util.List;

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
     * @parameter property="project.build.outputDirectory"
     */
    private File outputDirectory;

    /**
     * The list of resources we want to transfer.
     *
     * @parameter property="project.resources"
     */
    private List<Resource> resources;

    /**
     * list of additional excludes
     *
     * @parameter
     */
    private List<String> excludes;

    /**
     * Use processed resources if available
     *
     * @parameter default-value="false"
     */
    private boolean useProcessedResources;

    /**
     * list of additional includes
     *
     * @parameter
     */
    private List<String> includes;

    /**
     * Excludes files from webapp directory
     *
     * @parameter
     */
    private boolean excludeWarSourceDirectory = false;

    /**
     * Excludes files from resources directories.
     *
     * @parameter default-value="false"
     */
    private boolean excludeResources = false;

    /**
     * @parameter property="project"
     * @readonly
     * @required
     */
    protected MavenProject project;

    /**
     * [js only] Display possible errors in the code
     *
     * @parameter property="maven.yuicompressor.jswarn" default-value="true"
     */
    protected boolean jswarn;

    /**
     * Whether to skip execution.
     *
     * @parameter property="maven.yuicompressor.skip" default-value="false"
     */
    private boolean skip;

    /**
     * define if plugin must stop/fail on warnings.
     *
     * @parameter property="maven.yuicompressor.failOnWarning" default-value="false"
     */
    protected boolean failOnWarning;

    /**
     * @component
     */
    protected BuildContext buildContext;

    protected ErrorReporter4Mojo jsErrorReporter_;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (skip) {
                getLog().debug("run of yuicompressor-maven-plugin skipped");
                return;
            }
            if (failOnWarning) {
                jswarn = true;
            }
            jsErrorReporter_ = new ErrorReporter4Mojo(getLog(), jswarn, buildContext);
            beforeProcess();
            processDir(sourceDirectory, outputDirectory, null, useProcessedResources);
            if (!excludeResources) {
                for (Resource resource : resources) {
                    File destRoot = outputDirectory;
                    if (resource.getTargetPath() != null) {
                        destRoot = new File(outputDirectory, resource.getTargetPath());
                    }
                    processDir(new File(resource.getDirectory()), destRoot, resource.getExcludes(), useProcessedResources);
                }
            }
            if (!excludeWarSourceDirectory) {
                processDir(warSourceDirectory, webappDirectory, null, useProcessedResources);
            }
            afterProcess();
            getLog().info(String.format("nb warnings: %d, nb errors: %d", jsErrorReporter_.getWarningCnt(), jsErrorReporter_.getErrorCnt()));
            if (failOnWarning && (jsErrorReporter_.getWarningCnt() > 0)) {
                throw new MojoFailureException("warnings on " + this.getClass().getSimpleName() + "=> failure ! (see log)");
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
     *
     * see https://github.com/davidB/yuicompressor-maven-plugin/issues/19
     */
    private void processDir(File srcRoot, File destRoot, List<String> srcExcludes, boolean destAsSource) throws Exception {
        if (srcRoot == null) {
            return;
        }
        if (!srcRoot.exists()) {
            buildContext.addMessage(srcRoot, 0, 0, "Directory " + srcRoot.getPath() + " does not exists", BuildContext.SEVERITY_WARNING, null);
            getLog().info("Directory " + srcRoot.getPath() + " does not exists");
            return;
        }
        if (destRoot == null) {
            throw new MojoFailureException("destination directory for " + srcRoot + " is null");
        }
        Scanner scanner;
        if (!buildContext.isIncremental()) {
            DirectoryScanner dScanner = new DirectoryScanner();
            dScanner.setBasedir(srcRoot);
            scanner = dScanner;
        } else {
            scanner = buildContext.newScanner(srcRoot);
        }

        if (includes == null) {
            scanner.setIncludes(getDefaultIncludes());
        } else {
            scanner.setIncludes(includes.toArray(new String[0]));
        }

        if ((srcExcludes != null) && !srcExcludes.isEmpty()) {
            scanner.setExcludes(srcExcludes.toArray(EMPTY_STRING_ARRAY));
        }
        if ((excludes != null) && !excludes.isEmpty()) {
            scanner.setExcludes(excludes.toArray(EMPTY_STRING_ARRAY));
        }
        scanner.addDefaultExcludes();

        scanner.scan();

        String[] includedFiles = scanner.getIncludedFiles();
        if (includedFiles == null || includedFiles.length == 0) {
            if (buildContext.isIncremental()) {
                getLog().info("No files have changed, so skipping the processing");
            } else {
                getLog().info("No files to be processed");
            }
            return;
        }
        for (String name : includedFiles) {
            SourceFile src = new SourceFile(srcRoot, destRoot, name, destAsSource);
            jsErrorReporter_.setDefaultFileName("..." + src.toFile().getAbsolutePath().substring(src.toFile().getAbsolutePath().lastIndexOf('/') + 1));
            jsErrorReporter_.setFile(src.toFile());
            processFile(src);
        }
    }

    protected abstract void processFile(SourceFile src) throws Exception;
}
