package org.opensagetv.webplayer;

import java.io.File;
import java.util.Locale;

/** Authorized SageTV media source classification. This never accepts a user path directly. */
final class DiscSourceInfo {
    enum Kind { DVD_VIDEO_TS, DVD_PARENT, DVD_MOUNTED, DVD_ISO, DVD_DRIVE, VOB_FILE, IMPORTED_MKV, BLURAY_BDMV, OTHER }
    final Kind kind; final File source; final File videoTs; final boolean sageDvd; final boolean sageBluRay; final boolean dvdDrive;
    final boolean interactiveNative; final String stage; final String note;
    private DiscSourceInfo(Kind kind,File source,File videoTs,boolean sageDvd,boolean sageBluRay,boolean dvdDrive,boolean interactiveNative,String stage,String note){
        this.kind=kind;this.source=source;this.videoTs=videoTs;this.sageDvd=sageDvd;this.sageBluRay=sageBluRay;this.dvdDrive=dvdDrive;this.interactiveNative=interactiveNative;this.stage=stage;this.note=note;
    }

    static DiscSourceInfo inspect(SageApiBridge sage,Object mediaFile){
        boolean br=sage.isBluRay(mediaFile),dvd=sage.isDVD(mediaFile),drive=sage.isDVDDrive(mediaFile);File f=sage.getNumberOfSegments(mediaFile)>0?sage.getFileForSegment(mediaFile,0):null;
        return classify(f,dvd,br,drive);
    }

    static DiscSourceInfo classify(File f,boolean dvd,boolean br,boolean drive){
        if(drive)return new DiscSourceInfo(Kind.DVD_DRIVE,f,null,dvd,br,true,true,"stock-server-disc-vm","Physical DVD drive is owned/mounted by SageTV; browser plugin does not elevate privileges or mount media.");
        if(br||looksBdmv(f))return new DiscSourceInfo(Kind.BLURAY_BDMV,f,null,dvd,true,false,false,"unsupported-scope","Blu-ray/BDMV/BD-J and DRM handling are outside the proven DVD-Video path.");
        String ext=extension(f);
        if("iso".equals(ext))return new DiscSourceInfo(Kind.DVD_ISO,f,null,dvd,false,false,dvd,"stock-server-iso-mount","ISO playback requires SageTV/server-side mount support before the native DVD VM can push MPEG-PS. The plugin never invokes sudo/root or changes container privileges.");
        if(f!=null&&f.isDirectory()){
            if(eq(f.getName(),"VIDEO_TS")&&hasVideoTsIfo(f))return new DiscSourceInfo(Kind.DVD_VIDEO_TS,f,f,dvd,false,false,true,"native-dvd","Authorized VIDEO_TS directory; native SageTV DVD VM is the interactive route.");
            File child=findChildIgnoreCase(f,"VIDEO_TS");if(child!=null&&child.isDirectory()&&hasVideoTsIfo(child))return new DiscSourceInfo(dvd?Kind.DVD_MOUNTED:Kind.DVD_PARENT,f,child,dvd,false,false,true,"native-dvd","Authorized parent/mounted directory containing VIDEO_TS.");
        }
        if(f!=null&&eq(f.getName(),"VIDEO_TS.IFO")){File root=f.getParentFile();return new DiscSourceInfo(Kind.DVD_VIDEO_TS,f,root,dvd,false,false,true,"native-dvd","Authorized VIDEO_TS.IFO resolves to its VIDEO_TS directory.");}
        if("vob".equals(ext))return new DiscSourceInfo(Kind.VOB_FILE,f,null,dvd,false,false,false,"ordinary-file","Individual VOB playback is not a navigable DVD; menus/title VM metadata are unavailable.");
        if("mkv".equals(ext))return new DiscSourceInfo(Kind.IMPORTED_MKV,f,null,dvd,false,false,false,"ordinary-file","Imported MKV is ordinary media even when sourced from a disc; it is not labeled as interactive DVD.");
        if(dvd)return new DiscSourceInfo(Kind.DVD_MOUNTED,f,f!=null&&f.isDirectory()?findChildIgnoreCase(f,"VIDEO_TS"):null,true,false,false,true,"native-dvd","SageTV identifies this MediaFile as DVD content; source form requires field verification.");
        return new DiscSourceInfo(Kind.OTHER,f,null,false,false,false,false,"ordinary-file","Not classified as DVD-Video.");
    }

    boolean isDisc(){return kind==Kind.DVD_VIDEO_TS||kind==Kind.DVD_PARENT||kind==Kind.DVD_MOUNTED||kind==Kind.DVD_ISO||kind==Kind.DVD_DRIVE||kind==Kind.BLURAY_BDMV;}
    boolean canProbeTitles(){return videoTs!=null&&videoTs.isDirectory();}
    String json(){return "{\"kind\":\""+kind.name().toLowerCase(Locale.ROOT)+"\",\"stage\":\""+HttpUtil.json(stage)+"\",\"interactiveNative\":"+interactiveNative+",\"sageDvd\":"+sageDvd+",\"sageBluRay\":"+sageBluRay+",\"dvdDrive\":"+dvdDrive+",\"sourcePath\":\""+HttpUtil.json(source==null?"":source.getPath())+"\",\"videoTsPath\":\""+HttpUtil.json(videoTs==null?"":videoTs.getPath())+"\",\"note\":\""+HttpUtil.json(note)+"\"}";}

    private static boolean looksBdmv(File f){if(f==null)return false;if(f.isDirectory()){if(eq(f.getName(),"BDMV"))return true;File c=findChildIgnoreCase(f,"BDMV");return c!=null&&c.isDirectory();}File p=f.getParentFile();return p!=null&&eq(p.getName(),"BDMV");}
    private static boolean hasVideoTsIfo(File dir){File f=findChildIgnoreCase(dir,"VIDEO_TS.IFO");return f!=null&&f.isFile();}
    private static File findChildIgnoreCase(File dir,String name){if(dir==null||!dir.isDirectory())return null;File[] kids=dir.listFiles();if(kids==null)return null;for(File k:kids)if(eq(k.getName(),name))return k;return null;}
    private static String extension(File f){if(f==null)return "";String n=f.getName();int d=n.lastIndexOf('.');return d<0?"":n.substring(d+1).toLowerCase(Locale.ROOT);}
    private static boolean eq(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
}
