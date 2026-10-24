package com.lyricmotion.app;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> pendingFiles;
    private String pendingText="";
    private static final int PICK=100,SAVE=101;
    @Override public void onCreate(Bundle b){super.onCreate(b);
      web=new WebView(this);setContentView(web);
      web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);
      web.getSettings().setMediaPlaybackRequiresUserGesture(false);
      web.addJavascriptInterface(new Bridge(),"Android");
      web.setWebViewClient(new WebViewClient());
      web.setWebChromeClient(new WebChromeClient(){
        @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
          if(pendingFiles!=null)pendingFiles.onReceiveValue(null);pendingFiles=cb;
          try{startActivityForResult(p.createIntent(),PICK);}catch(Exception e){pendingFiles=null;cb.onReceiveValue(null);Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}
          return true;
        }
      });
      web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);
      if(req==PICK&&pendingFiles!=null){pendingFiles.onReceiveValue(res==RESULT_OK?WebChromeClient.FileChooserParams.parseResult(res,data):null);pendingFiles=null;}
      if(req==SAVE){if(res==RESULT_OK&&data!=null&&data.getData()!=null){try(java.io.OutputStream os=getContentResolver().openOutputStream(data.getData())){os.write(pendingText.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"저장 완료",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"저장 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}}pendingText="";}
    }
    public class Bridge {
      @JavascriptInterface public void save(String filename,String mime,String value){runOnUiThread(()->{
        pendingText=value;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(i,SAVE);
      });}
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}