package io.github.gerbesh.unusualcopper.gameplay;

import java.util.*;

/** Ordered small network chunks, with a new identity for every input/configuration. */
public final class EnchantmentCatalog<T> {
    public static final int PAGE_SIZE=64;
    public static final int PAGE_BYTES=65536;
    private UUID identity;
    private int total;
    private final List<T> entries=new ArrayList<>();
    public boolean begin(UUID next,int count,int registrySize){
        if(next==null||count<0||count>registrySize)throw new IllegalArgumentException("Enchantment catalog size");
        if(next.equals(identity)){
            if(total!=count)throw new IllegalArgumentException("Changed catalog size");return false;
        }
        identity=next;total=count;entries.clear();return true;
    }
    public boolean append(UUID token,int offset,List<T> page){
        if(!Objects.equals(identity,token))return false;
        if(offset<0||offset>entries.size()||page.size()>PAGE_SIZE||(long)offset+page.size()>total||page.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Enchantment catalog page");
        for(int i=0;i<page.size();i++){
            int index=offset+i;
            if(index<entries.size()){
                if(!Objects.equals(entries.get(index),page.get(i)))throw new IllegalArgumentException("Changed catalog entry");
            }else entries.add(page.get(i));
        }
        return true;
    }
    public int loaded(){return entries.size();}
    public int total(){return total;}
    public List<T> entries(){return List.copyOf(entries);}
    public boolean complete(){return entries.size()==total;}
}
