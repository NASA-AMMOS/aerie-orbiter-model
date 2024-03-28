 KPL/MK

           File name: latest_meta_kernel.tm

           Here are the SPICE kernels required for my application
           program.

           Note that kernels are loaded in the order listed. Thus
           we need to list the highest priority kernel last.


           \begindata

           PATH_VALUES       = ( 'spice/kernels' )

           PATH_SYMBOLS      = ( 'KERNELS' )


           KERNELS_TO_LOAD = (

              '$KERNELS/de440s.bsp',
              '$KERNELS/naif0012.tls',
              '$KERNELS/pck00011.tpc',
              '$KERNELS/gm_de440.tpc',
              '$KERNELS/earth_topo_201023.tf',
              '$KERNELS/earthstns_itrf93_201023.bsp',
              '$KERNELS/veritas_science_example.bsp',
              '$KERNELS/veritas_cruise_example.bsp',
              '$KERNELS/veritas_aerobraking_example.bsp'

           )

           \begintext

           End of meta-kernel
