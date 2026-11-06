package vn.uth.court;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.imageio.*;
import org.springframework.stereotype.Component;

/** Bounded, derived JPEG previews. Original uploaded files and QR images are never modified. */
@Component
public class CourtPhotoPreview {
  private final Map<String,byte[]> cache=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){
    @Override protected boolean removeEldestEntry(Map.Entry<String,byte[]> entry){return size()>32;}
  });
  private final Semaphore encoders=new Semaphore(2);
  public Optional<byte[]> preview(Path path,int width) throws IOException {
    if(!Set.of(480,800,1440).contains(width))throw new IllegalArgumentException("Kích thước ảnh xem trước không hợp lệ.");
    String key=path.toString()+":"+Files.getLastModifiedTime(path).toMillis()+":"+Files.size(path)+":"+width;
    byte[] cached=cache.get(key);if(cached!=null)return Optional.of(cached);
    // Leave EXIF-bearing JPEGs to the browser to preserve the original orientation.
    if(path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*[.]jpe?g")){
      byte[] header;try(var source=Files.newInputStream(path)){header=source.readNBytes(65536);}
      for(int i=0;i+5<header.length;i++)if(header[i]==69&&header[i+1]==120&&header[i+2]==105&&header[i+3]==102&&header[i+4]==0&&header[i+5]==0)return Optional.empty();
    }
    if(!encoders.tryAcquire())return Optional.empty();
    try(var input=ImageIO.createImageInputStream(path.toFile())) {
      var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return Optional.empty();var reader=readers.next();
      try {
        reader.setInput(input,true,true);int originalWidth=reader.getWidth(0),originalHeight=reader.getHeight(0);
        if((long)originalWidth*originalHeight>40_000_000L||originalWidth<1||originalHeight<1||originalWidth>20000||originalHeight>20000)return Optional.empty();
        var parameters=reader.getDefaultReadParam();int sampling=Math.max(1,originalWidth/width);parameters.setSourceSubsampling(sampling,sampling,0,0);
        BufferedImage decoded=reader.read(0,parameters);int w=Math.min(width,originalWidth),h=Math.max(1,(int)Math.round((double)originalHeight*w/originalWidth));
        var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var graphics=result.createGraphics();
        try{graphics.setColor(Color.WHITE);graphics.fillRect(0,0,w,h);graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(decoded,0,0,w,h,null);}finally{graphics.dispose();decoded.flush();}
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();var bytes=new ByteArrayOutputStream();
        try(var output=ImageIO.createImageOutputStream(bytes)){
          writer.setOutput(output);var settings=writer.getDefaultWriteParam();settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);settings.setCompressionQuality(.82f);writer.write(null,new IIOImage(result,null,null),settings);
        }finally{writer.dispose();result.flush();}
        byte[] encoded=bytes.toByteArray();if(encoded.length<=600000)cache.put(key,encoded);return Optional.of(encoded);
      }finally{reader.dispose();}
    }catch(javax.imageio.IIOException ex){return Optional.empty();}finally{encoders.release();}
  }
}
