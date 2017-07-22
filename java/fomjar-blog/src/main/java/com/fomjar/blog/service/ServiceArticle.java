package com.fomjar.blog.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fomjar.blog.config.ServiceConfig;

@Service
@SuppressWarnings("unchecked")
public class ServiceArticle {
    
    @Autowired
    private ServiceConfig config;
    
    public ServiceArticle() {}
    
    private static String new_aid() {
        return "article-" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private static String get_path(String aid) {
        return ServiceConfig.PATH_ARTICLE + "/" + aid + ".md";
    }
    
    private static String get_name(String data) {
        String name = data.split("\n")[0];
        while (name.startsWith("#")) name = name.substring(1);
        name = name.trim();
        return name;
    }
    
    public void article_edit(String author, String data) throws UnsupportedEncodingException, IOException {
        article_edit(new_aid(), author, data);
    }
    
    public void article_edit(String aid, String author, String data) throws UnsupportedEncodingException, IOException {
        if (null == aid || 0 == aid.length()) aid = new_aid();
        
        String name = get_name(data);
        String path = get_path(aid);
        
        Files.write(new File(path).toPath(), data.getBytes("utf-8"),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        
        Map<String, Object> article = new HashMap<>();
        article.put("aid",      aid);
        article.put("name",     name);
        article.put("author",   author);
        article.put("path",     path);
        article.put("time.update", System.currentTimeMillis());
        if (config.mon_article_list.config().containsKey(aid)) {
            Map<String, Object> article_old = (Map<String, Object>) config.mon_article_list.config().get(aid);
            article_old.putAll(article);
        } else {
            article.put("time.create", System.currentTimeMillis());
            config.mon_article_list.config().put(aid, article);
        }
    }
    
    public Map<String, Object> article_view(String aid) throws IOException {
        if (null == aid || 0 == aid.length()) throw new IllegalArgumentException("null aid");
        
        String path = get_path(aid);
        File file = new File(path);
        if (!file.isFile()) throw new FileNotFoundException("article not found for aid: " + aid);
        
        Map<String, Object> article = (Map<String, Object>) config.mon_article_list.config().get(aid);
        if (null == article) throw new FileNotFoundException("article not found for aid: " + aid);
        
        byte[] buf = Files.readAllBytes(new File(path).toPath());
        String data = new String(buf, "utf-8");
        article = new HashMap<>(article);
        article.put("data", data);
        return article;
    }
    
    public Map<String, Object> article_list() {
        return config.mon_article_list.config();
    }

}