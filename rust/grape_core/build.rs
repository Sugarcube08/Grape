fn main() {
    let udl_source = "../../ffi/grape.udl";
    let udl_dest = "src/grape.udl";
    
    std::fs::copy(udl_source, udl_dest).unwrap_or_else(|err| {
        panic!("Failed to copy UDL file from {} to {}: {}", udl_source, udl_dest, err);
    });
    
    uniffi::generate_scaffolding("src/grape.udl").unwrap();
}
