(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('alertSimilarCaseList', {
            controller: function($scope, FilteringSrv, PaginatedQuerySrv, CaseResolutionStatus) {
                var self = this;

                self.CaseResolutionStatus = CaseResolutionStatus;

                self.$onInit = function() {
                    this.filtering = new FilteringSrv('case', 'alert.dialog.similar-cases', {
                        version: 'v1',
                        defaults: {
                            showFilters: true,
                            showStats: false,
                            pageSize: 15,
                            sort: ['-startDate']
                        },
                        defaultFilter: []
                    });

                    self.filtering.initContext(this.alertId)
                        .then(function() {
                            self.load();

                            $scope.$watch('$cmp.list.pageSize', function (newValue) {
                                self.filtering.setPageSize(newValue);
                            });

                            $scope.$watch('$cmp.list.total', function (total) {
                                self.onListLoad({count: total});
                            });
                        });
                };

                this.load = function() {
                    this.list = new PaginatedQuerySrv({
                        name: 'alert-similar-cases',
                        skipStream: true,
                        version: 'v1',
                        loadAll: true,
                        pageSize: 15,
                        operations: [
                            {'_name': 'getAlert', 'idOrName': this.alertId},
                            {'_name': 'similarCases'}
                        ],
                        onUpdate: function() {}
                    });
                };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/alert/similar-case-list.component.html',
            bindings: {
                alertId: '<',
                onListLoad: '&'
            }
        });
})();
