package org.opensagetv.webplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure path matching policy used only after SageTV direct MediaFile lookup fails. */
final class AuthorizedPathMatcher {
    private AuthorizedPathMatcher() {}

    static int uniqueBestMatch(File requested, List<File> candidates) {
        if(requested==null||candidates==null||candidates.isEmpty())return -1;
        String[] wanted=parts(requested);
        int best=-1,bestScore=0,ties=0;
        for(int i=0;i<candidates.size();i++){
            File candidate=candidates.get(i);if(candidate==null)continue;
            String[] have=parts(candidate);int score=suffixScore(wanted,have,caseInsensitive(requested,candidate));
            if(score>bestScore){bestScore=score;best=i;ties=1;}else if(score>0&&score==bestScore){ties++;}
        }
        // Never fall back on basename alone. Requiring parent+basename permits
        // stale mapped roots while preventing an unrelated same-name recording.
        return bestScore>=2&&ties==1?best:-1;
    }

    static int bestSuffixScore(File requested, File candidate){
        return suffixScore(parts(requested),parts(candidate),caseInsensitive(requested,candidate));
    }

    private static int suffixScore(String[] a,String[] b,boolean ci){
        int i=a.length-1,j=b.length-1,score=0;
        while(i>=0&&j>=0){String x=a[i],y=b[j];if(ci){x=x.toLowerCase(Locale.ROOT);y=y.toLowerCase(Locale.ROOT);}if(!x.equals(y))break;score++;i--;j--;}
        return score;
    }
    private static String[] parts(File f){
        String p=f.getPath().replace('\\','/');String[] raw=p.split("/+");List<String> out=new ArrayList<String>();for(String s:raw)if(!s.isEmpty()&&!".".equals(s))out.add(s);return out.toArray(new String[out.size()]);
    }
    private static boolean caseInsensitive(File a,File b){return windowsStyle(a.getPath())||windowsStyle(b.getPath())||File.separatorChar=='\\';}
    private static boolean windowsStyle(String p){return p!=null&&(p.matches("^[A-Za-z]:.*")||p.startsWith("\\\\")||p.startsWith("//"));}
}
