JOB_FOLDER="nc/Airshipctl"
JOB_NAME="Airshipctl"
PARENT_FOLDER="development"
folder("${PARENT_FOLDER}") {
    displayName("${PARENT_FOLDER}")
    description("Folder for ${PARENT_FOLDER}")
}
FOLDER="AirshipCtl"
PATH="${PARENT_FOLDER}/${FOLDER}"
folder("${PATH}") {
    displayName("${FOLDER}")
    description("Folder for ${FOLDER}")
}
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('GERRIT_REFSPEC')
            defaultValue('master')
            description('The gerrit refspec')
            trim(true)
        }
        stringParam {
            name ('NODE_LABEL')
            defaultValue('airship1')
            description('The node label of the worker')
            trim(true)
        }
    }
    triggers {
        cron("H */4 * * *")
        gerritTrigger {
            silentMode(false)
            serverName('gerrit-service')
            gerritProjects {
                gerritProject {
                    compareType('PLAIN')
                    pattern("airship/airshipctl")
                    branches {
                        branch {
                            compareType('ANT')
                            pattern("**")
                        }
                    }
                    forbiddenFilePaths {
                        filePath {
                            compareType('ANT')
                            pattern("zuul.d/**")
                        }
                        filePath {
                            compareType('ANT')
                            pattern("docs/**")
                        }
                    }
                    disableStrictForbiddenFileVerification(true)
                }
            }
            triggerOnEvents {
                patchsetCreated {
                    excludeDrafts(false)
                    excludeTrivialRebase(false)
                    excludeNoCodeChange(false)
                    excludePrivateState(false)
                    excludeWipState(true)
                }
                topicChanged()
                draftPublished()
                commentAddedContains {
                   commentAddedCommentContains('recheck')
                }
            }
        }
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/airshipctl.groovy")
        lightweight()
      }
    }
}

JOB_FOLDER="nc/Treasuremap"
JOB_NAME="Treasuremapv2-AirshipctlKnownState"
FOLDER="TreasureMap"
PATH="${PARENT_FOLDER}/${FOLDER}"
folder("${PATH}") {
    displayName("${FOLDER}")
    description("Folder for ${FOLDER}")
}
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('GERRIT_REFSPEC')
            defaultValue('master')
            description('The gerrit refspec')
            trim(true)
        }
        stringParam {
            name ('SOPS_PGP_FP')
            defaultValue('FBC7B9E2A4F9289AC0C1D4843D16CEE4A27381B4')
            description('SOPS_PGP_FP')
            trim(true)
        }
        stringParam {
            name ('NODE_LABEL')
            defaultValue('airship12')
            description('The node label of the worker')
            trim(true)
        }
        stringParam {
            name ('AIRSHIPCTL_REF')
            defaultValue('c355b600d7a3fd66f03016ad9384a13cf17e80e7')
            description('Airshipctl Version to use for deployment')
            trim(true)
        }
        textParam {
            name ('SOPS_IMPORT_PGP')
            defaultValue('''-----BEGIN PGP PRIVATE KEY BLOCK-----

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
=VjGL
-----END PGP PRIVATE KEY BLOCK-----
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQHYBF1oQYgBBADPuVP6Jdk/J/TbNa9dXirp/zzwK18ZqNudNqQGN3H+2aSgxXwL
wlRfzy7rB3CU6Ewjzk9EVYeYztTIkGHL0JZ1CCTiBJArlHO0bHQQ7CPeKPkhIhkj
eA8yu9dcU77oYC2xbwgf43KYzfMKSGEybg+sBO+bH+Y6paJK54V2cuS3GwARAQAB
AAP+Jjf5BXtVP1OAr5xvCYS77JWzhpTUSIpS7dgR0br91GAC9DmhmyBEGeSqwz95
LUyYRbY9y1rZOfpEGCrIc5GLPOQytO9XMIzaS3dpzfGhla/spaKN4vJDvIOl+ruT
bInDdCRSmqXCfm2478OhOquc0H0a46eSmoaYeKdE3E8QZiECANxUL/dFk5j8NyPo
ZcwXw9Mv0A8UrynRcqht3Scti9k7dbsHylcObM305LFdcoNnSfNAIJhxfjbiXyGW
vwT2/qMCAPFatq3gvVjy6wKKylioi5cVwbLv9L+OaRXdR/Dy2bh/t3ujnsliV4+R
f7k3rHOQeaMLTnyfcz8AenL5IOe8RSkCANFpBgyzxCcV48Mm+FWDxjrSJ4/msRnN
gxqAPRrdpm7e1uebtBkPh4ch4oCW5/lLsRN23LUVIXYJRwyFfRjehCio0rRTU09Q
UyBGdW5jdGlvbmFsIFRlc3RzIEtleSAyIChodHRwczovL2dpdGh1Yi5jb20vbW96
aWxsYS9zb3BzLykgPHNlY29wc0Btb3ppbGxhLmNvbT6IzgQTAQgAOBYhBNcikEM4
S8xgMmxvudhyDZV8PTB0BQJdaEGIAhsDBQsJCAcCBhUKCQgLAgQWAgMBAh4BAheA
AAoJENhyDZV8PTB0R2cD/2YwaJ43iGueaAzByFnl+mUEBQJ4HhH4p7BIdx6B9AjE
3yLe8I4dqqYXxyZzaJ9d+KiqxJBT0l1GXt3H5M32yDJZqzXB9PTWP3yx8+Q1CuCs
7EL/bhJD1/sLdumVc77bmQtcI9NSiYyPzN/2ZqtV5RU14Loh24VFEjuHGvO0jI3+
nQHYBF1oQYgBBAChXi00fmpEs0Jiq0zOyYm9i749VoOsNReoB/5ix1QCimwVZKe1
D37IP5Qqysxy+LIQc4lJ+Q8foNOx1Aev5+TDyv+iU82D9xr9uPLLbA82k3AZ04Or
BjrZ/Yt1NZhuaHzciZCPpmqzF9kqVqAZc+vMiKZL1WZjS7O1FwaidY1vXwARAQAB
AAP+L0wUQeOfsD0+gv8khyPJTJZOD1pxQ6NYKLcXF8rG0+vQnECha098YKNKAXTp
kfVU8795iQYIKcQQ6Hl2O1fj1AxJE/iZYrqfm7UZz3bQ7ROSsAEPZ5GDOjKfbwsz
E6bWVH+PhS1azlvtTs9JezUtK0Wl9s+81FOrZtnUUskmWtECAMNNs9ujUt6GHv/J
NXVaSmk1z8QXitPHbAJLDMj4xVDysJWZV95eplC+RUSiLz5HeP2AQgh1D9Rv2bA5
c7OcJ3kCANOEkA0hVpXCI0FKrsihOf0NUOaAtS6CQNFlaIkrLwssJQY8pGYbRfRa
3krNJPyOlXmezV2/CsX3EqA9KXXen5cB/iSmMJO4WndGJTe7YzUEnnY/P2TKg1fN
s6v5Lf39j5Ll8V5rVDT7ApAw0IKS8fzpbdHP0HcizutlF6l44YaAXMGfhoi2BBgB
CAAgFiEE1yKQQzhLzGAybG+52HINlXw9MHQFAl1oQYgCGwwACgkQ2HINlXw9MHTD
HwQAv+ui718AT2hw2pK9JaNuTxjllrH+KPMlrov0P8oXHPCohC5cxM5sJ6tCQ0qH
XyeWoDE8V31btqFVAQyrr0wy0gntl1L/trnwMHoP8a/xa0RHNk5C7hmcuhTHbQey
JNbiRJZpCIZ1OyrF17+q6u9YBPjwqp8KrJ/0ryy2kyb7ZRM=
=+tJ6
-----END PGP PRIVATE KEY BLOCK-----
-----BEGIN PGP PUBLIC KEY BLOCK-----

mQENBF1oQV0BCAC1iFfE7H3uu0hbWbRYVMoz5zZ91ACHETCOMVxN8GOG4SV0l8aQ
wmK9QWkYxhi52LnicVD3D7Uy75+J3zkvEDQ15C0AZ8UHXp4JlSQuXpFhrOhfYUF/
6pr/QexT+hQjOacvY4qfnj4xKa/AGdv5vPIygtQumE6r3GhEVAxQ1GSwtCWSU3Zl
3Uqf7S8kDvJTemtR2UkVfpXcMd4AmMKgt7fVhPO8eFotqTLPvz/iClzER+q61fLA
d1rP9YlmY46MJp/PffPicWdJiKv2i6ynKcIwkrQyP6V2ZzYi/gAhNJst3ZlMfsiN
ekCtcow9Bn44uxW3U8W02FNQSNyn6V6QPDIXABEBAAG0U1NPUFMgRnVuY3Rpb25h
bCBUZXN0cyBLZXkgMSAoaHR0cHM6Ly9naXRodWIuY29tL21vemlsbGEvc29wcy8p
IDxzZWNvcHNAbW96aWxsYS5jb20+iQFOBBMBCAA4FiEE+8e54qT5KJrAwdSEPRbO
5KJzgbQFAl1oQV0CGwMFCwkIBwIGFQoJCAsCBBYCAwECHgECF4AACgkQPRbO5KJz
gbTDcQf7Bp7e2zY9pBBXTgDASQl31SSHp9WkRUV5iqPVC9iPCELggteBGMwIpbDl
obc6O8/06foxWctTUaaciPBo2+jeWFTO+DNvB7oXIArqr5673QHLh6jEABBjyt91
rvta2wYF1XJBgxpui9aLICsCptFNIRvHeKUrXBI4fG5z3CDs/EOoY8K/AAYJUF+E
RtmvmisiE/m20UpbYRmkBJy25c89Wcn12I1SUJA3H3hGwvZCYp8hY1HPxxQUtU+D
ZBIpryi0xQqExGAlYqck7G03F+AD7/csaT1LEdCtWRLNwE8UkvfUF6liF0SgzxFo
1pp3gBU4swds9yO9wNe12JY/M5A/BLkBDQRdaEFdAQgAtun8JhSpNAKvOXwWX2nF
hnMXTJp4viMhlAZEdmMXEi27B2DM/nRzldjxGZoNUBSVbJNj2kx5ZUDl0o6eOpCh
vRaGuCOpYqOuSQvD8FnX0NgQULwuTZ+MawsaezktJEjDSBM1R6uASeJwDZj4hcUn
PgyAIESajPdowEkEjdYt261fGOLLcVoVdtqzOMBkLVdrK/FD1kGR9jnSlKEYDV9D
veBUBQGdqkgWXjS5BKcae07viC6xMa9AJS4pizyDALB2k0HQOelZNihOGXYUuvkc
s2Fivl0Tk3OCfH9XDvFehbYRHmkRDoMuKUDSzdy6tFBAkL0CPlXAWI6kQklaBEp1
9QARAQABiQE2BBgBCAAgFiEE+8e54qT5KJrAwdSEPRbO5KJzgbQFAl1oQV0CGwwA
CgkQPRbO5KJzgbS7zwgAndbf532OXo9HwPH+yQQmzQCLDFL6P4V7LcFrrydYItTE
hxqI3tbb96MKXRAt+G5Mw6JjRkWhwzbU3jE7D7XBMHw7GriTTU9QltNHg7VUpSSa
iTfVcSNErzsaqbjbA7jMs7VWzOq4LZo6Efy8UDKg5qcqLFaTQrzQZYNHNfM+kLAi
UPU8m7vwmz6oJWsjHkQKUhKhHptlpwMwdHkoacqDO0x2H6H91l/PnDm4ZG6FybJt
cjr98i+p52/XOo81nLgX7tcFS3nrN9HNdgKg1ZW3yrzg8NOaFCVA8qLDgLk//M3q
DixOxiurECkFrMvt/bDxEGpN5GVy550MmyUZQrkuqg==
=Zs2s
-----END PGP PUBLIC KEY BLOCK-----
-----BEGIN PGP PUBLIC KEY BLOCK-----

mI0EXWhBiAEEAM+5U/ol2T8n9Ns1r11eKun/PPArXxmo2502pAY3cf7ZpKDFfAvC
VF/PLusHcJToTCPOT0RVh5jO1MiQYcvQlnUIJOIEkCuUc7RsdBDsI94o+SEiGSN4
DzK711xTvuhgLbFvCB/jcpjN8wpIYTJuD6wE75sf5jqlokrnhXZy5LcbABEBAAG0
U1NPUFMgRnVuY3Rpb25hbCBUZXN0cyBLZXkgMiAoaHR0cHM6Ly9naXRodWIuY29t
L21vemlsbGEvc29wcy8pIDxzZWNvcHNAbW96aWxsYS5jb20+iM4EEwEIADgWIQTX
IpBDOEvMYDJsb7nYcg2VfD0wdAUCXWhBiAIbAwULCQgHAgYVCgkICwIEFgIDAQIe
AQIXgAAKCRDYcg2VfD0wdEdnA/9mMGieN4hrnmgMwchZ5fplBAUCeB4R+KewSHce
gfQIxN8i3vCOHaqmF8cmc2ifXfioqsSQU9JdRl7dx+TN9sgyWas1wfT01j98sfPk
NQrgrOxC/24SQ9f7C3bplXO+25kLXCPTUomMj8zf9marVeUVNeC6IduFRRI7hxrz
tIyN/riNBF1oQYgBBAChXi00fmpEs0Jiq0zOyYm9i749VoOsNReoB/5ix1QCimwV
ZKe1D37IP5Qqysxy+LIQc4lJ+Q8foNOx1Aev5+TDyv+iU82D9xr9uPLLbA82k3AZ
04OrBjrZ/Yt1NZhuaHzciZCPpmqzF9kqVqAZc+vMiKZL1WZjS7O1FwaidY1vXwAR
AQABiLYEGAEIACAWIQTXIpBDOEvMYDJsb7nYcg2VfD0wdAUCXWhBiAIbDAAKCRDY
cg2VfD0wdMMfBAC/66LvXwBPaHDakr0lo25PGOWWsf4o8yWui/Q/yhcc8KiELlzE
zmwnq0JDSodfJ5agMTxXfVu2oVUBDKuvTDLSCe2XUv+2ufAweg/xr/FrREc2TkLu
GZy6FMdtB7Ik1uJElmkIhnU7KsXXv6rq71gE+PCqnwqsn/SvLLaTJvtlEw==
=PafV
-----END PGP PUBLIC KEY BLOCK-----
-----BEGIN PGP PUBLIC KEY BLOCK-----

mQENBF1oQaYBCADsCw223WDj2ISnkZQJ07NS7ER/ft2Tz6FPzsMqz5JmGlwQantH
MzjqNGE5du1TBK+yCIuzn+P/iokmOdFjkH20OHHCEmgBQPQ2WxpR4Bc/jDvswoL2
2amknIYStf+SgCwtNPT51RS7/5brN5pVn4GjDYg+IRLk33bxz1kN3933olkMFHJF
hrr1rcE1uxt3j2CEPzfKAUyviKXkSl41IAopE2e69zsVg0YaensTnE/hY45r1q0W
ABXoJLg4H6UtHZWWGg7vmtQYCpiiUIaWZ3YB449Ur4jxXeIZPUqhliqOoUk8a7En
1ZC1hGLw0jaENeaHywVGi9ZqABmOVYmvItI3ABEBAAG0U1NPUFMgRnVuY3Rpb25h
bCBUZXN0cyBLZXkgMyAoaHR0cHM6Ly9naXRodWIuY29tL21vemlsbGEvc29wcy8p
IDxzZWNvcHNAbW96aWxsYS5jb20+iQFOBBMBCAA4FiEEthGi+fEdD/glaIBRGfm1
2uqR/4YFAl1oQaYCGwMFCwkIBwIGFQoJCAsCBBYCAwECHgECF4AACgkQGfm12uqR
/4Yc3QgA2WcMlCCUB4uyyvKq1a8ZTBli0YzIxT+KslL+PbOxfhoxglxPRClB0/l6
stG3RD0UXKq9cTe9f5nI96XJRqUzePfKtixSieirNTZswiHIYifYfg460gXYAIsV
deztRGxEaMUVq6vajkA69yGbi2nExaQrPQAgcOoyEcGhsQVi5M3rbIGIJtK/K777
lAvWskMWjEL/VIQ3qHJapBwhtyK8pKiQ1U2ssvXSP15RouUU/8PdD6d82dLlLfBm
eiyQCeJYQyR/geJAvhyBO+Jy+SS2RNCE6FGirACzRh3SSLpKAvieoMHqYzav8kMh
zSMa5230SKpartbsgFu7t39zsc3t6LkBDQRdaEGmAQgA60kJghTuxvZaNjXCZBnj
0+z+NSnfhdPaGgdUStXDCbzsO2l/6kvlWMwwS3VuDwGjx1vLWt9KWxmL85+qLE2H
FuRpH4sJFa+0HraY9SP1UxgO5ydommRKdLDw5iQIaJACMSsMAHHOQCJ9VuNE1gt8
gOb5J902gRahgIKpGrrmtI6x3Zmu1ryKrBH9Ln60kYvrU14ANPYtIvOwqCNxeU4C
9q1q4/b73RXg2Gri8alGb2HMIaeK6u7i5MlEOqkQof2FkmGg3nEw/6Cvcnf4jesJ
ibKa69vC6n39ZJ0eRhmhr3tw3MP76Laayhqq8T+Ffog68A3Oe+i6+f7PyLe0oaHA
uQARAQABiQE2BBgBCAAgFiEEthGi+fEdD/glaIBRGfm12uqR/4YFAl1oQaYCGwwA
CgkQGfm12uqR/4b5lwgAmVCa0XYeco6Ec+Iz0CLBvNXDFH/KsP/ypWK5duzZRKeb
D30cDwQHUFWH5WivGZ5nJ+Rs9zkD7a07omMTRmCsrjD4I3xDGMTU23l+gBSC5+9R
B6bOi81ngH3OLaSbeh2t21PDEf57M94WFNlw2LVgMvZ6S4rs7I4FZgm75h4EGGuY
It1l8SqNWcKDm9Kz/qG0lqeSGGFnQqmSBFH0Vb0hus/XErU2r3fQr1lDj0VKpOIO
J0Ys9rmI6yEPTi+GhFr1bHKwZMinz5lcHnOl8xye48tsrOtHMGN17/B6hUUGzd+W
TphrOfnfTO1YCkg1nEB5E2Raj/KV+ohqPvjE+KhE7Q==
=gqhE
-----END PGP PUBLIC KEY BLOCK-----''')
            description('SOPS_IMPORT_PGP')
        }
    }
    triggers {
        cron("H */6 * * *")
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/Treasuremapv2-AirshipctlKnownState.groovy")
        lightweight()
      }
    }
}

JOB_FOLDER="nc/Maintenance"
JOB_NAME="Airshipctl_StatusReport"
FOLDER="MaintenanceJobs"
PATH="${PARENT_FOLDER}/${FOLDER}"
folder("${PATH}") {
    displayName("${FOLDER}")
    description("Folder for ${FOLDER}")
}
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('NO_OF_DAYS')
            defaultValue('1')
            description('Number of Days for which report needs to be extracted')
            trim(true)
        }
        stringParam {
            name ('LIMIT')
            defaultValue('50')
            description('Build Limit')
            trim(true)
        }
        stringParam {
            name ('NODE_LABEL')
            defaultValue('airship12')
            description('The node label of the worker')
            trim(true)
        }
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/AirshipctlStatusReport.groovy")
        lightweight()
      }
    }
}

JOB_NAME="CleanupOldBuildLogs"
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('NO_OF_DAYS_TO_CLEANUP')
            defaultValue('5')
            description('Number of days for which logs to be cleaned up')
            trim(true)
        }
        stringParam {
            name ('HOST')
            defaultValue('10.254.125.160')
            description('Host to run cleanup script on')
            trim(true)
        }
        stringParam {
            name ('NODE_LABEL')
            defaultValue('stl3')
            description('The node label of the worker')
            trim(true)
        }
    }
    triggers {
        cron("H 0 * * *")
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/CleanupOldBuildLogs.groovy")
        lightweight()
      }
    }
}

JOB_NAME="WorkerMaintenance"
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('WORKER_LABEL')
            defaultValue('airship12')
            description('Worker Node label')
            trim(true)
        }
    }
    triggers {
        cron("H 0 * * *")
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/WorkerMaintenance.groovy")
        lightweight()
      }
    }
}

JOB_NAME="AirshipCtlBot"
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('NODE_LABEL')
            defaultValue('stl3')
            description('Worker Node label')
            trim(true)
        }
        stringParam {
            name ('SYNC_TIME_WINDOW')
            defaultValue('20m')
            description('Last N minutes to check gerrit issues for github BOT to update')
            trim(true)
        }
    }
    triggers {
        cron("H/20 * * * *")
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/AirshipCtlBot.groovy")
        lightweight()
      }
    }
}

JOB_NAME="VinoBot"
pipelineJob("${PATH}/${JOB_NAME}") {
    parameters {
        stringParam {
            name ('NODE_LABEL')
            defaultValue('stl3')
            description('Worker Node label')
            trim(true)
        }
        stringParam {
            name ('SYNC_TIME_WINDOW')
            defaultValue('20m')
            description('Last N minutes to check gerrit issues for github BOT to update')
            trim(true)
        }
    }
    triggers {
        cron("H/20 * * * *")
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url('https://review.gerrithub.io/att-comdev/cicd.git')
            }
            branch('*/master')
          }
        }
        scriptPath("${JOB_FOLDER}/VinoBot.groovy")
        lightweight()
      }
    }
}
