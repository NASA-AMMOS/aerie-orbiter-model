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
              '$KERNELS/earth_070425_370426_predict.bpc',
              '$KERNELS/earthstns_itrf93_201023.bsp',
              '$KERNELS/mar097_2020_2040.bsp',
              '$KERNELS/mro_psp.bsp'

           )

           \begintext

           End of meta-kernel
