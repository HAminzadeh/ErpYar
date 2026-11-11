package ir.erp.launcher;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON manifest is generated locally, never contains Docker credentials. */
final class ImageManifest {
  record Entry(String imageRef, String repoDigest, String imageId, String os, String architecture,
               String platform, String tarFile, String sha256, long sizeBytes,
               String createdAt, String source, String verifiedAt) {
    Map<String,Object> json() {
      Map<String,Object> m=new LinkedHashMap<>();
      m.put("imageRef",imageRef); m.put("repoDigest",repoDigest); m.put("imageId",imageId);
      m.put("os",os); m.put("architecture",architecture); m.put("platform",platform);
      m.put("tarFile",tarFile); m.put("sha256",sha256); m.put("sizeBytes",sizeBytes);
      m.put("createdAt",createdAt); m.put("source",source); m.put("verifiedAt",verifiedAt);
      return m;
    }
  }
  final List<Entry> entries;
  ImageManifest(List<Entry> entries) { this.entries=List.copyOf(entries); }
  static ImageManifest empty() { return new ImageManifest(List.of()); }
  Entry find(String ref,String platform) {
    return entries.stream().filter(e -> e.imageRef().equals(ref) && e.platform().equals(platform))
        .findFirst().orElse(null);
  }
  ImageManifest replace(Entry entry) {
    List<Entry> list=new ArrayList<>();
    for (Entry old:entries) if (!(old.imageRef().equals(entry.imageRef()) && old.platform().equals(entry.platform()))) list.add(old);
    list.add(entry);return new ImageManifest(list);
  }
  String serialize() {
    Map<String,Object> doc=new LinkedHashMap<>();doc.put("schemaVersion",1);doc.put("images",entries.stream().map(Entry::json).toList());
    return JsonCodec.stringify(doc) + "\n";
  }
  @SuppressWarnings("unchecked")
  static ImageManifest parse(String json) {
    Object data=JsonCodec.parse(json);
    if (!(data instanceof Map<?,?> root)) throw new StartupException("Cache manifest must be an object");
    if (!Long.valueOf(1).equals(root.get("schemaVersion"))) throw new StartupException("Cache manifest schemaVersion must be 1");
    if (!(root.get("images") instanceof List<?> rows)) throw new StartupException("Cache manifest images must be an array");
    if (rows.size()>32) throw new StartupException("Cache manifest has too many images");
    List<Entry> entries=new ArrayList<>();
    for (Object o:rows) {
      if (!(o instanceof Map<?,?> m)) throw new StartupException("Invalid image entry");
      Object size=m.get("sizeBytes");
      if (!(size instanceof Long n)) throw new StartupException("sizeBytes must be an integer");
      Entry e=new Entry(field(m,"imageRef"),field(m,"repoDigest"),field(m,"imageId"),
          field(m,"os"),field(m,"architecture"),field(m,"platform"),field(m,"tarFile"),
          field(m,"sha256"),n,field(m,"createdAt"),field(m,"source"),field(m,"verifiedAt"));
      ManifestValidator.validateEntry(e);
      if (entries.stream().anyMatch(p->p.imageRef().equals(e.imageRef()) && p.platform().equals(e.platform())))
        throw new StartupException("Duplicate image/platform entry in cache manifest");
      entries.add(e);
    }
    return new ImageManifest(entries);
  }
  private static String field(Map<?,?> map,String key) {
    Object v=map.get(key);
    if (!(v instanceof String s)) throw new StartupException("Invalid or missing image manifest field: " + key);
    return s;
  }
}
