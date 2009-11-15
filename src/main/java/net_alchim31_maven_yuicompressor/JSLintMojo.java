package net_alchim31_maven_yuicompressor;


/**
 * Check JS files with jslint.
 *
 * @goal jslint
 * @phase process-resources
 *
 * @author David Bernard
 * @created 2007-08-29
 */
// @SuppressWarnings("unchecked")
public class JSLintMojo extends MojoSupport {
    private JSLintChecker jslint_;

    @Override
    protected String[] getDefaultIncludes() throws Exception {
        return new String[] { "**/**.js" };
    }

    @Override
    public void beforeProcess() throws Exception {
        jslint_ = new JSLintChecker();
    }

    @Override
    public void afterProcess() throws Exception {
    }

    @Override
    protected void processFile(SourceFile src) throws Exception {
        getLog().info("check file :" + src.toFile());
        jslint_.check(src.toFile(), jsErrorReporter_);
    }
}
