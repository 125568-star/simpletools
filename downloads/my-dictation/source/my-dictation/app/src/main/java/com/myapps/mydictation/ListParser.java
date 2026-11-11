package com.myapps.mydictation;

import java.util.*;

public final class ListParser {
    private ListParser(){}
    public static List<String> parse(String transcript){
        if(transcript==null)return Collections.emptyList();
        String s=transcript.trim().replaceFirst("(?i)^(create|make|add|this is) (a |my )?(shopping |to-do |todo )?list( of|:)?[ \\t]*","");
        s=s.replaceFirst("(?i)^(this is )?(a |my )?(to-do|todo) (item|list)[:,.]?[ \\t]*","");
        String[] segments=s.split("(?i)[,;\\n]+|\\s+and\\s+");
        List<String> result=new ArrayList<>();
        for(String segment:segments){String item=segment.trim().replaceAll("^[•*\\-\\d.) ]+","").replaceAll("[. ]+$","");if(!item.isEmpty())result.add(item);}
        return result;
    }
}
