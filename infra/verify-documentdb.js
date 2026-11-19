// Checks that the DocumentDB cluster accepts the TTL index and really deletes expired documents.
// It uses a scratch collection (ttl_probe) in the database of the connection string and drops it at the end.
// Run it from the VM (the connection string is read from the environment and is never printed):
//   docker run --rm -i -e MONGODB_URI mongo:7 sh -c 'mongosh "$MONGODB_URI" --quiet --file /dev/stdin' < infra/verify-documentdb.js
// Result: OK or FALLA lines. It takes up to ~3 minutes (the TTL monitor runs about once a minute).
const probe = db.getCollection("ttl_probe");
probe.drop();

// 1. The index can be created with the same options MS08 uses.
probe.createIndex({ fecha_hora: 1 }, { name: "ttl_probe_idx", expireAfterSeconds: 30 });
const index = probe.getIndexes().find((i) => i.name === "ttl_probe_idx");
print(index && index.expireAfterSeconds === 30
    ? "OK    el indice TTL se crea y conserva expireAfterSeconds=30"
    : "FALLA el indice TTL no conserva expireAfterSeconds: " + JSON.stringify(index));

// 2. A document already older than the TTL is deleted by the database.
probe.insertOne({ _id: "viejo", fecha_hora: new Date(Date.now() - 10 * 60 * 1000) });
probe.insertOne({ _id: "nuevo", fecha_hora: new Date() });
let deleted = false;
for (let i = 0; i < 18 && !deleted; i++) {
    sleep(10000);
    deleted = probe.countDocuments({ _id: "viejo" }) === 0;
}
print(deleted
    ? "OK    la base borro el documento vencido (TTL activo)"
    : "FALLA el documento vencido sigue despues de 3 min: usar AUDIT_CLEANUP_JOB_ENABLED=true");
print(probe.countDocuments({ _id: "nuevo" }) === 1
    ? "OK    el documento reciente se conserva"
    : "FALLA el documento reciente desaparecio");

// 3. The other indexes of MS08 can be created (a compound one is enough as a sample).
probe.createIndex({ modulo: 1, accion: 1, fecha_hora: -1 }, { name: "compuesto_probe" });
print(probe.getIndexes().some((i) => i.name === "compuesto_probe")
    ? "OK    los indices compuestos se crean"
    : "FALLA no se creo el indice compuesto");
probe.drop();
