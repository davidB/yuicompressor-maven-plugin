package net.sf.alchim.mojo.yuicompressor;

import java.io.File;

public class SourceFile {

    private File srcRoot_;
    private File destRoot_;
    private boolean destAsSource_;
    private String rpath_;
    private String extension_;

    public SourceFile(File srcRoot, File destRoot, String name, boolean destAsSource) throws Exception {
        srcRoot_ = srcRoot;
        destRoot_ = destRoot;
        destAsSource_ = destAsSource;
        rpath_ = name;
        int sep = rpath_.lastIndexOf('.');
        if (sep>0) {
            extension_ = rpath_.substring(sep);
            rpath_ = rpath_.substring(0, sep);
        } else {
            extension_ = "";
        }
    }

    public File toFile() {
        String frpath = rpath_ + extension_;
        if (destAsSource_) {
            File defaultDest = new File(destRoot_, frpath);
            if (defaultDest.exists() && defaultDest.canRead()) {
                return defaultDest;
            }
        }
        return new File(srcRoot_, frpath);
    }

    public File toDestFile(String suffix) {
        return new File(destRoot_, rpath_ + suffix + extension_);
    }

    public String getExtension() {
        return extension_;
    }
}
