package org.opensagetv.webplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Resolves a MiniClient path to exactly one SageTV-owned MediaFile. */
final class AuthorizedMediaResolver {
    static final class Resolution {
        final Object mediaFile; final File requested; final File matched; final String mode;
        Resolution(Object mediaFile,File requested,File matched,String mode){this.mediaFile=mediaFile;this.requested=requested;this.matched=matched;this.mode=mode;}
    }
    private AuthorizedMediaResolver() {}

    static Resolution resolve(SageApiBridge sage,File requested){
        if(sage==null)throw new IllegalStateException("sagex API is unavailable");
        if(requested==null)throw new IllegalArgumentException("MiniClient media URL has no filesystem path");
        Object direct=sage.getMediaFileForFilePath(requested);
        if(direct!=null)return new Resolution(direct,requested,findExactSegment(sage,direct,requested),"sage-exact");

        List<Object> media=new ArrayList<Object>();List<File> files=new ArrayList<File>();
        for(Object candidate:sage.getMediaFiles()){
            if(candidate==null)continue;int count=sage.getNumberOfSegments(candidate);
            for(int i=0;i<count;i++){File f=sage.getFileForSegment(candidate,i);if(f!=null){media.add(candidate);files.add(f);}}
        }
        int index=AuthorizedPathMatcher.uniqueBestMatch(requested,files);
        if(index<0){
            int sameName=0;for(File f:files)if(f!=null&&requested.getName().equals(f.getName()))sameName++;
            if(sameName>1)throw new IllegalArgumentException("MiniClient media path is ambiguous: "+sameName+" SageTV files share basename '"+requested.getName()+"'. Use an exact/authorized path.");
            throw new IllegalArgumentException("SageTV could not resolve MiniClient media URL to one authorized MediaFile: "+requested.getPath());
        }
        return new Resolution(media.get(index),requested,files.get(index),"unique-path-suffix");
    }

    private static File findExactSegment(SageApiBridge sage,Object mf,File requested){
        int count=sage.getNumberOfSegments(mf);for(int i=0;i<count;i++){File f=sage.getFileForSegment(mf,i);if(f!=null&&AuthorizedPathMatcher.bestSuffixScore(requested,f)>=Math.min(2,Math.max(1,pathParts(requested))))return f;}return requested;
    }
    private static int pathParts(File f){String p=f.getPath().replace('\\','/');int n=0;for(String s:p.split("/+"))if(!s.isEmpty())n++;return n;}
}
