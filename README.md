# AlsoUpdater

`AlsoUpdater` 是一个基于 `Java` 的文件更新器，利用 `Minio` 从对象存储服务器获取 `manifest.json` 文件，检查本地文件的完整性，并下载更新的文件。

## 功能

从 `Minio` 对象存储服务器下载文件。

在下载文件之前，先根据文件的大小和 `MD5` 哈希值进行验证。

通过 `config.json` 配置文件进行设置。

只有在文件丢失或已过时的情况下才会下载文件。

## 示例

`minecraft` 服务器的服主想要实现 `minecraft` 相关内容的热更新(比如插件、设置等)，希望在服务器更新时服务器玩家能及时收到更新

那么服主可以：

1. 部署 `minio` (推荐较早的版本，原因就不说了...)
2. 提供服务器玩家配置文件
3. 告知服务器玩家在启动器或其它位置添加额外的启动命令，使得在玩家启动游戏时先执行本项目，再打开游戏
4. 搞定！

`minio` 桶结构参考

```bash
bucket
 │  manifest.json
 │
 └─files
     └─mods
             xxx.jar
```

## 环境要求

`Java 8` 或更高版本。

`Minio` 服务器或兼容的对象存储服务。

有效的 `config.json` 配置文件。

## 设置与配置

### 克隆仓库

```bash
git clone https://github.com/alsojiangshi/alsoupdater
cd alsoupdater
```

### 配置 `config.json` 文件

更新器通过 `config.json` 文件进行配置。如果文件不存在，将会生成一个默认文件。

以下是配置文件的示例结构：

```json
{
    "endpoint": "http://127.0.0.1:9000",
    "access_key": "YOUR_ACCESS_KEY",
    "secret_key": "YOUR_SECRET_KEY",
    "bucket": "modpack",
    "region": "shanghai",
    "download_dir": "./modpack"
}
```

`endpoint:` ` Minio` 服务器的 `URL`（或任何兼容的 `S3` 兼容服务器）。

`access_key:` `Minio` 的访问密钥。

`secret_key:` `Minio` 的私密密钥。

`bucket:` 存储文件的桶（`Bucket`）的名称。

`region:` `Minio` 服务器的区域（如果适用）。

`download_dir:` 文件下载保存的本地目录。

## 运行应用程序

下载 `release` 或使用构建工具 `Maven` 编译出 `.jar` 包。

双击 `.jar` 包运行或使用命令

```bash
java -jar alsoupdater.jar
```

运行后，会在 `.jar` 包同目录下寻找配置文件 `config.json` 若未找到，则会自行生成一个。

当 `config.json` 配置正确时，将触发更新过程，下载 `manifest.json` 文件，检查文件的一致性，并下载任何需要更新的文件。

## 工作原理

### 加载配置：

读取 `config.json` 文件来配置 `Minio` 连接的详细信息。

### 获取 `manifest.json`：

为 `manifest.json` 文件生成一个预签名 `URL`，并将其下载到本地。

### 验证文件：

遍历 `manifest.json` 中列出的每个文件，检查文件是否存在、大小是否匹配、`MD5` 是否一致。如果文件丢失或已过时，将触发下载。

### 下载丢失或更新的文件：

为每个文件生成一个预签名 `URL`，并通过该 `URL` 从 `Minio` 服务器下载文件。

### 错误处理

如果发生任何错误（如缺少配置项或下载失败），程序会打印详细的错误信息和堆栈跟踪。

#### 示例输出

```bash
正在获取 manifest.json ...
开始校验和下载 3 个文件...
[已存在] files/README.md
[下载] files/image.png -> ./modpack/image.png
[下载] files/config.json -> ./modpack/config.json
更新完成！
```

## TODO

- 增强对更多 `oss` 平台的兼容性
- 更好地便于集成到 `minecraft` 启动器
- 缩小 `release` 体积(if possible)

## 许可协议

此项目使用 `Apache-2.0` 许可协议 - 详见 [LICENSE](https://github.com/alsojiangshi/alsoupdater/blob/main/LICENSE.txt)
