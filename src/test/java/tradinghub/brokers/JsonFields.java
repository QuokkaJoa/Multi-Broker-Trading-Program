package tradinghub.brokers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.TreeSet;

/** 실서버 응답 모양을 녹화와 비교한다 (값은 안 본다). networkTest용. */
public final class JsonFields {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonFields() {}

    /** 녹화에는 있는데 실서버 응답에는 없는 필드 경로. 배열 안은 `[]`로 적는다. 실서버 배열이 비었으면 그 안은 건너뛴다. */
    public static Set<String> missing(String recorded, String live) {
        Set<String> want = new TreeSet<>(), have = new TreeSet<>(), empty = new TreeSet<>();
        walk(JSON.readTree(recorded), "", want, new TreeSet<>());
        walk(JSON.readTree(live), "", have, empty);
        want.removeAll(have);
        want.removeIf(path -> empty.stream().anyMatch(e -> path.startsWith(e + ".")));
        return want;
    }

    private static void walk(JsonNode node, String path, Set<String> paths, Set<String> emptyArrays) {
        if (node.isObject()) {
            node.properties().forEach(p -> {
                String child = path.isEmpty() ? p.getKey() : path + "." + p.getKey();
                paths.add(child);
                walk(p.getValue(), child, paths, emptyArrays);
            });
        } else if (node.isArray()) {
            if (node.isEmpty()) emptyArrays.add(path + "[]");
            for (JsonNode item : node) walk(item, path + "[]", paths, emptyArrays);
        }
    }
}
