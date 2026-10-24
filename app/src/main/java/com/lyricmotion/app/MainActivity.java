package com.lyricmotion.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

@UnstableApi
public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> pendingFiles;
    private Uri audioUri, imageUri;
    private String chooserType = "";
    private String pendingText = "";
    private File readyVideo;
    private static final int PICK=100, SAVE=101, SAVE_VIDEO=102;
    private Transformer transformer;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(27,36,64));
        getWindow().getDecorView().setSystemUiVisibility(0);
        web=new WebView(this);setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(),"Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if(pendingFiles!=null)pendingFiles.onReceiveValue(null);
                pendingFiles=cb;
                String[] accepts=p.getAcceptTypes();
                chooserType=accepts.length>0 ? accepts[0] : "";
                try { startActivityForResult(p.createIntent(),PICK); }
                catch(Exception e) {pendingFiles=null;cb.onReceiveValue(null);message("파일 선택 실패: "+e.getMessage());}
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }
    private void message(String m){Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    private void notifyWeb(String kind,String info){
        web.evaluateJavascript("window.onNativeExport&&window.onNativeExport("+JSONObject.quote(kind)+","+JSONObject.quote(info)+")",null);
    }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==PICK&&pendingFiles!=null){
            Uri[] result=res==RESULT_OK?WebChromeClient.FileChooserParams.parseResult(res,data):null;
            if(result!=null&&result.length>0) {
                if(chooserType.contains("audio"))audioUri=result[0];
                if(chooserType.contains("image"))imageUri=result[0];
            }
            pendingFiles.onReceiveValue(result);pendingFiles=null;
        } else if(req==SAVE){
            if(res==RESULT_OK&&data!=null&&data.getData()!=null){
                try(OutputStream os=getContentResolver().openOutputStream(data.getData())){os.write(pendingText.getBytes(StandardCharsets.UTF_8));message("저장 완료");}
                catch(Exception e){message("저장 실패: "+e.getMessage());}
            }
            pendingText="";
        } else if(req==SAVE_VIDEO){
            if(res==RESULT_OK&&data!=null&&data.getData()!=null&&readyVideo!=null){
                try(InputStream in=new FileInputStream(readyVideo); OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    message("MP4 저장 완료");notifyWeb("done","MP4 저장 완료");
                }catch(Exception e){message("영상 저장 오류: "+e.getMessage());notifyWeb("error",e.getMessage());}
            }
        }
    }
    private long getDuration(){
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{r.setDataSource(this,audioUri);String s=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);return Long.parseLong(s);}
        finally{try{r.release();}catch(java.io.IOException ignored){}}
    }
    private Bitmap createFrame(String lyric,int color,int width,int height,Bitmap art,boolean emphasize){
        Bitmap bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);c.drawColor(color);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        if(art!=null) {
            float limitW=width*.70f,limitH=height*.79f;
            float scale=Math.min(limitW/art.getWidth(),limitH/art.getHeight());
            float w=art.getWidth()*scale,h=art.getHeight()*scale;
            p.setAlpha(195);c.drawBitmap(art,null,new RectF((width-w)/2,15,(width+w)/2,15+h),p);p.setAlpha(255);
        }
        p.setColor(0x9F10182B);c.drawRoundRect(36,height*.67f,width-36,height*.94f,22,22,p);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
        float size=emphasize?53:44; p.setTextSize(size);p.setShadowLayer(3,1,3,0x88000000);
        String s=lyric==null?"":lyric.trim();
        java.util.List<String> lines=new ArrayList<>();
        while(!s.isEmpty()&&lines.size()<4){
            int split=s.length();
            while(split>1&&p.measureText(s.substring(0,split))>width-120)split--;
            if(split<s.length()){int space=Math.max(s.lastIndexOf(' ',split),s.lastIndexOf('　',split));if(space>2)split=space+1;}
            lines.add(s.substring(0,split).trim());s=s.substring(split).trim();
        }
        int total=lines.size();float y=height*.81f-(total-1)*size*.55f;
        for(String line:lines){c.drawText(line,width/2f,y,p);y+=size*1.13f;}
        return bmp;
    }
    private void export(String json,String background) {
        if(audioUri==null){notifyWeb("error","먼저 MP3 파일을 선택해줘.");return;}
        notifyWeb("progress","영상 프레임 구성 중");
        try {
            JSONArray items=new JSONArray(json);
            long duration=getDuration();
            if(duration<1000)throw new IllegalArgumentException("음원 길이를 읽지 못했습니다");
            int color=Color.parseColor(background);
            Bitmap art=null;
            if(imageUri!=null){
                try(InputStream in=getContentResolver().openInputStream(imageUri)){
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
                    art=BitmapFactory.decodeStream(in,null,options);
                }catch(Exception ignored){}
            }
            File frameDir=new File(getCacheDir(),"mvframes");frameDir.mkdirs();
            File[] old=frameDir.listFiles();if(old!=null)for(File f:old)f.delete();
            ArrayList<EditedMediaItem> imageClips=new ArrayList<>();
            long current=0;
            ArrayList<Long> starts=new ArrayList<>();
            ArrayList<String> lyrics=new ArrayList<>();
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);
                starts.add(Math.max(0,Math.round(item.optDouble("start",0)*1000)));
                lyrics.add(item.optString("text",""));
            }
            // Preserve the time before the first lyric and any gaps.
            ArrayList<Long> stops=new ArrayList<>(starts);stops.add(duration);
            ArrayList<Long> boundaries=new ArrayList<>();boundaries.add(0L);
            for(long t:starts)if(t>0&&t<duration)boundaries.add(t);
            boundaries.add(duration);Collections.sort(boundaries);
            ArrayList<Long> unique=new ArrayList<>();
            for(long t:boundaries)if(unique.isEmpty()||t>unique.get(unique.size()-1))unique.add(t);
            for(int k=0;k<unique.size()-1;k++){
                long begin=unique.get(k),end=unique.get(k+1);
                int index=-1;
                for(int j=0;j<starts.size();j++)if(starts.get(j)<=begin)index=j;
                String lyric=index<0?"":lyrics.get(index);
                long segment=end-begin;if(segment<=0)continue;
                // Each lyric has a short bounce-in followed by its main still.
                int pieces=segment>=650&&index>=0?2:1;
                for(int part=0;part<pieces;part++){
                    long pieceDuration=pieces==1?segment:(part==0?Math.min(180,segment/3):segment-Math.min(180,segment/3));
                    if(pieceDuration<50)continue;
                    Bitmap bitmap=createFrame(lyric,color,960,540,art,part==0&&pieces==2);
                    File png=new File(frameDir,String.format(Locale.US,"frame_%04d.png",imageClips.size()));
                    try(FileOutputStream fos=new FileOutputStream(png)){bitmap.compress(Bitmap.CompressFormat.PNG,90,fos);}
                    bitmap.recycle();
                    MediaItem media=new MediaItem.Builder().setUri(Uri.fromFile(png)).setImageDurationMs(pieceDuration).build();
                    imageClips.add(new EditedMediaItem.Builder(media).setFrameRate(15).build());
                }
            }
            if(art!=null)art.recycle();
            if(imageClips.isEmpty())throw new IllegalArgumentException("영상에 넣을 가사 구간이 없습니다");
            EditedMediaItemSequence videoSequence=new EditedMediaItemSequence.Builder(imageClips).build();
            EditedMediaItem audio=new EditedMediaItem.Builder(MediaItem.fromUri(audioUri)).build();
            EditedMediaItemSequence audioSequence=new EditedMediaItemSequence.Builder(Collections.singletonList(audio)).build();
            Composition composition=new Composition.Builder(videoSequence,audioSequence).build();
            readyVideo=new File(getCacheDir(),"LyricMotion-output.mp4");
            if(readyVideo.exists())readyVideo.delete();
            transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition c,ExportResult result){
                    transformer=null;notifyWeb("ready","영상 생성 완료. 파일 저장 화면을 엽니다.");
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("video/mp4");
                    intent.putExtra(Intent.EXTRA_TITLE,"LyricMotion-MV.mp4");
                    startActivityForResult(intent,SAVE_VIDEO);
                }
                @Override public void onError(Composition c,ExportResult result,ExportException exception){
                    transformer=null;notifyWeb("error",exception.getMessage());message("영상 렌더링 실패: "+exception.getMessage());
                }
            }).build();
            notifyWeb("progress","MP4 영상 인코딩 중");
            transformer.start(composition,readyVideo.getAbsolutePath());
        }catch(Exception e){notifyWeb("error",e.getMessage());message("영상 제작 오류: "+e.getMessage());}
    }
    private boolean syncing=false;
    private void runOfflineSync(String language) {
        if(syncing){notifyWeb("syncError","분석 중입니다");return;}
        if(audioUri==null){notifyWeb("syncError","먼저 MP3 파일을 선택해줘");return;}
        syncing=true;
        notifyWeb("syncProgress","모델 확인 중 (최초 1회 약 142 MB 다운로드)");
        new Thread(()->{
            try{
                File model=new File(getFilesDir(),"ggml-base.bin");
                if(!model.exists() || model.length()<100000000){
                    File tmp=new File(getFilesDir(),"ggml-base.bin.tmp");
                    java.net.URL url=new java.net.URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin");
                    java.net.HttpURLConnection conn=(java.net.HttpURLConnection)url.openConnection();
                    conn.setConnectTimeout(20000);conn.setReadTimeout(45000);
                    try(java.io.InputStream in=conn.getInputStream();java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){
                        byte[] buf=new byte[65536];int n;long total=0;long last=-1;
                        while((n=in.read(buf))!=-1){
                            out.write(buf,0,n);total+=n;
                            long mb=total/1048576;
                            if(mb!=last && mb%10==0){last=mb;long progress=mb;runOnUiThread(()->notifyWeb("syncProgress","AI 모델 다운로드: "+progress+" MB"));}
                        }
                    }finally{conn.disconnect();}
                    if(tmp.length()<100000000)throw new Exception("모델 다운로드가 불완전합니다");
                    if(!tmp.renameTo(model))throw new Exception("모델 저장에 실패했습니다");
                }
                runOnUiThread(()->notifyWeb("syncProgress","MP3 음원 디코딩 중"));
                float[] pcm=AudioPcm.decode16k(this,audioUri);
                if(pcm.length<16000)throw new Exception("분석할 오디오가 너무 짧습니다");
                runOnUiThread(()->notifyWeb("syncProgress","Whisper 음성 인식 중 (시간이 걸릴 수 있습니다)"));
                String result=WhisperEngine.transcribe(model.getAbsolutePath(),pcm,language);
                if(result.contains("\"error\""))throw new Exception(result);
                runOnUiThread(()->notifyWeb("syncResult",result));
            }catch(Throwable ex){
                String msg=ex.getMessage()==null?ex.toString():ex.getMessage();
                runOnUiThread(()->notifyWeb("syncError",msg));
            }finally{syncing=false;}
        }).start();
    }
    public class Bridge {
        @JavascriptInterface public void autoSync(String language){runOnUiThread(()->runOfflineSync(language));}
        @JavascriptInterface public void save(String filename,String mime,String value){runOnUiThread(()->{
            pendingText=value;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,filename);
            startActivityForResult(i,SAVE);
        });}
        @JavascriptInterface public void exportVideo(String lyricsJson,String color){runOnUiThread(()->export(lyricsJson,color));}
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
