# 规则源维护说明

`rules-src` 是开发期规则源目录。Android App 运行时仍只读取：

```text
app/src/main/assets/rules/rule-set.json
```

日常修改规则时，优先修改单条 YAML：

```text
rules-src/rules/baseline/*.yaml
rules-src/rules/additional/*.yaml
```

每条规则都有 `enabled` 字段：

```yaml
enabled: true
```

临时停用规则时改为：

```yaml
enabled: false
```

停用的规则仍会进入生成后的 JSON，但 App 执行和统计会跳过它。需要恢复时改回 `true` 并重新生成。

新增附加规则可以从模板复制：

```bash
cp rules-src/rules/additional/_template.yaml rules-src/rules/additional/ADD_GRASS_000.yaml
```

修改后运行：

```bash
python tools/validate_rules.py
python tools/build_rules.py
```

提交时保留修改过的 YAML，并提交重新生成的 `app/src/main/assets/rules/rule-set.json`。
