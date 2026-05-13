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
      "adapters :: support",
      "adapters :: error",
      "adapters :: bkash"
    })
package pay.conflux.backend.paymentcore;
