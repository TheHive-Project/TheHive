(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseExportCtrl',
        function($scope, $state, $stateParams, $timeout, PSearchSrv, CaseTabsSrv, categories) {
            var self = this;

            this.caseId = $stateParams.caseId;
            this.searchForm = {};
            var tabName = 'export-' + this.caseId;

            // MISP category/type map
            this.categories = categories;

            // Add tab
            CaseTabsSrv.addTab(tabName, {
                name: tabName,
                label: 'Export',
                closable: true,
                state: 'app.case.export',
                params: {}
            });

            // Select tab
            $timeout(function() {
                CaseTabsSrv.activateTab(tabName);
            }, 0);


            this.artifacts = PSearchSrv(this.caseId, 'case_artifact', {
                scope: $scope,
                baseFilter: {
                    '_and': [{
                        '_parent': {
                            "_type": "case",
                            "_query": {
                                "_id": $scope.caseId
                            }
                        }
                    }, {
                        'ioc': true
                    }, {
                        'status': 'Ok'
                    }]
                },
                filter: this.searchForm.searchQuery !== '' ? {
                    _string: this.searchForm.searchQuery
                } : '',
                loadAll: true,
                sort: '-startDate',
                pageSize: 30,
                onUpdate: function () {
                    self.enhanceArtifacts();
                },
                nstats: true
            });

            this.enhanceArtifacts = function(data) {
                console.log(data);
            }

        }
    );
})();
