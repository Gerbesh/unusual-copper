package io.github.gerbesh.unusualcopper.machine;

import java.util.*;

/**
 * E5B: deterministic, read-only bill-of-materials DAG for explicitly whitelisted recipes.
 * No external inventory is reserved by this calculation; only actual physical extraction
 * into persistent BoundCargo may subsequently reserve ingredients.
 */
public final class ProductionDependencyPlanner {
    public static final int MAX_DEPTH=16;
    public static final int MAX_NODES=128;
    public static final int MAX_REQUEST=4096;
    public static final long MAX_INTERMEDIATE=1_000_000L;

    private ProductionDependencyPlanner() {}

    public record RecipeSpec(String id,String output,int yield,String machine,int cue,int ticks,
                             Map<String,Integer> consumed,Set<String> reusable) {
        public RecipeSpec {
            if(id==null||id.isBlank()||output==null||output.isBlank()||machine==null||machine.isBlank()
                    ||yield<1||yield>64||cue<0||ticks<1||consumed==null||reusable==null)
                throw new IllegalArgumentException("Invalid production recipe");
            consumed=Map.copyOf(consumed);
            reusable=Set.copyOf(reusable);
            if(consumed.isEmpty()||consumed.size()>9||consumed.values().stream().anyMatch(n->n==null||n<1||n>64)
                    ||consumed.keySet().stream().anyMatch(k->k==null||k.isBlank())
                    ||reusable.stream().anyMatch(k->k==null||k.isBlank()))
                throw new IllegalArgumentException("Invalid production ingredients");
        }
    }

    public record Stage(String recipe,String machine,String output,long operations,long produced,long cue,long ticks) {}
    public record Plan(List<Stage> stages,Map<String,Long> rawMaterials,Set<String> reusableTools,
                       long totalCue,long totalTicks) {
        public Plan {
            stages=List.copyOf(stages);rawMaterials=Map.copyOf(rawMaterials);
            reusableTools=Set.copyOf(reusableTools);
        }
    }

    /** Rejects recipes with duplicate producers, rather than silently selecting the last one. */
    public static Plan plan(Collection<RecipeSpec> specs,String target,int count) {
        if(count<1||count>MAX_REQUEST||target==null||target.isBlank())
            throw new IllegalArgumentException("Invalid production order");
        Objects.requireNonNull(specs,"specs");
        if(specs.size()>MAX_NODES)throw new IllegalArgumentException("Too many recipe nodes");
        Map<String,RecipeSpec> recipes=new LinkedHashMap<>();
        for(var spec:specs) {
            Objects.requireNonNull(spec,"spec");
            if(recipes.putIfAbsent(spec.output(),spec)!=null)
                throw new IllegalArgumentException("Ambiguous producer: "+spec.output());
        }
        var context=new Expansion(recipes);
        context.require(target,count,0);
        long cost=0,ticks=0;
        var steps=new ArrayList<Stage>();
        for(var entry:context.operations.entrySet()) {
            var spec=entry.getKey();
            long op=entry.getValue();
            long produced=Math.multiplyExact(op,spec.yield());
            long stepCost=Math.multiplyExact(op,spec.cue());
            long stepTicks=Math.multiplyExact(op,spec.ticks());
            cost=Math.addExact(cost,stepCost);
            ticks=Math.addExact(ticks,stepTicks);
            steps.add(new Stage(spec.id(),spec.machine(),spec.output(),op,produced,stepCost,stepTicks));
        }
        return new Plan(steps,context.raw,context.tools,cost,ticks);
    }

    private static final class Expansion {
        private final Map<String,RecipeSpec> recipes;
        private final Map<String,Long> spare=new HashMap<>();
        private final LinkedHashMap<RecipeSpec,Long> operations=new LinkedHashMap<>();
        private final TreeMap<String,Long> raw=new TreeMap<>();
        private final TreeSet<String> tools=new TreeSet<>();
        private final Set<String> visiting=new HashSet<>();

        private Expansion(Map<String,RecipeSpec> recipes){this.recipes=recipes;}

        private void require(String item,long wanted,int depth) {
            if(wanted<1||wanted>MAX_INTERMEDIATE||depth>MAX_DEPTH)
                throw new IllegalArgumentException("Production expansion limit exceeded");
            // Never let a stock surplus mask a cyclic recipe definition currently being traversed.
            if(visiting.contains(item))throw new IllegalArgumentException("Recursive production cycle: "+item);
            long held=spare.getOrDefault(item,0L);
            if(held>=wanted){spare.put(item,held-wanted);return;}
            spare.remove(item);
            long missing=wanted-held;
            RecipeSpec recipe=recipes.get(item);
            if(recipe==null){
                long total=raw.merge(item,missing,Math::addExact);
                if(total>MAX_INTERMEDIATE)throw new IllegalArgumentException("Raw ingredient limit exceeded");
                return;
            }
            if(operations.size()>=MAX_NODES&&!operations.containsKey(recipe))
                throw new IllegalArgumentException("Production graph exceeds 128 nodes");
            visiting.add(item);
            try{
                long batches=divideCeil(missing,recipe.yield());
                if(batches>MAX_INTERMEDIATE)throw new IllegalArgumentException("Too many operations");
                // Sort inputs: same recipes produce the same DAG regardless of JSON/map ordering.
                var ingredients=new TreeMap<>(recipe.consumed());
                for(var e:ingredients.entrySet()) {
                    long count=Math.multiplyExact(batches,e.getValue().longValue());
                    require(e.getKey(),count,depth+1);
                }
                for(String tool:recipe.reusable()) {
                    tools.add(tool);
                    // A reusable mold is not multiplied by batch count.
                    if(!recipes.containsKey(tool))continue;
                    // A crafted reusable tool is still a one-time physical prerequisite.
                    require(tool,1,depth+1);
                }
                long produced=Math.multiplyExact(batches,recipe.yield());
                long surplus=produced-missing;
                if(surplus>0)spare.merge(item,surplus,Math::addExact);
                operations.merge(recipe,batches,Math::addExact);
                if(operations.size()>MAX_NODES)
                    throw new IllegalArgumentException("Production graph exceeds 128 nodes");
            } finally {visiting.remove(item);}
        }
        private static long divideCeil(long n,long d){return n/d+(n%d==0?0:1);}
    }
}
