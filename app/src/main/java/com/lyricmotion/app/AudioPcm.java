package com.lyricmotion.app;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public final class AudioPcm {
    public static float[] decode16k(Context ctx, Uri input) throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec codec=null;
        try {
            extractor.setDataSource(ctx,input,null);
            int index=-1;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){index=i;break;}
            }
            if(index<0)throw new Exception("오디오 트랙 없음");
            extractor.selectTrack(index);
            MediaFormat format=extractor.getTrackFormat(index);
            int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);codec.start();
            ArrayList<Float> original=new ArrayList<>();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean inputDone=false, outputDone=false;
            while(!outputDone){
                if(!inputDone){
                    int idx=codec.dequeueInputBuffer(10000);
                    if(idx>=0){
                        ByteBuffer buf=codec.getInputBuffer(idx);buf.clear();
                        int size=extractor.readSampleData(buf,0);
                        if(size<0){codec.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else{codec.queueInputBuffer(idx,0,size,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int idx=codec.dequeueOutputBuffer(info,10000);
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat out=codec.getOutputFormat();
                    channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    sampleRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                }else if(idx>=0){
                    ByteBuffer buf=codec.getOutputBuffer(idx);
                    if(info.size>0){
                        buf.position(info.offset);buf.limit(info.offset+info.size);
                        buf.order(ByteOrder.LITTLE_ENDIAN);
                        int encoding=codec.getOutputFormat().containsKey(MediaFormat.KEY_PCM_ENCODING)?
                            codec.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                        int bytesPer=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        int frameSize=bytesPer*channels;
                        while(buf.remaining()>=frameSize){
                            float sum=0;
                            for(int ch=0;ch<channels;ch++){
                                sum += encoding==AudioFormat.ENCODING_PCM_FLOAT?buf.getFloat():buf.getShort()/32768f;
                            }
                            original.add(sum/channels);
                        }
                    }
                    codec.releaseOutputBuffer(idx,false);
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputDone=true;
                }
            }
            int count=(int)Math.floor((double)original.size()*16000/sampleRate);
            float[] result=new float[count];
            for(int i=0;i<count;i++){
                double pos=(double)i*sampleRate/16000;
                int a=Math.min((int)pos,original.size()-1),b=Math.min(a+1,original.size()-1);
                double t=pos-a;result[i]=(float)(original.get(a)*(1-t)+original.get(b)*t);
            }
            return result;
        } finally {
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            extractor.release();
        }
    }
}
