@org.springframework.modulith.ApplicationModule(
    displayName = "Payment Core",
    allowedDependencies = {
      "common",
      "provisioning",
      "provisioning :: usecase",
      "risk",
      "risk :: usecase",
      "quota",
      "quota :: usecase",
      "adapters",
      "adapters :: port",
      "adapters :: support"
    })
package pay.conflux.backend.paymentcore;
