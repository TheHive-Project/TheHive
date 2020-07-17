(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('alertObservableList', {
            controller: function($scope, FilteringSrv, PaginatedQuerySrv) {
                var self = this;

                self.$onInit = function() {
                    this.filtering = new FilteringSrv('case_artifact', 'alert.dialog.observables', {
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
                        name: 'alert-observables',
                        skipStream: true,
                        version: 'v1',
                        sort: self.filtering.context.sort,
                        loadAll: false,
                        pageSize: self.filtering.context.pageSize,
                        filter: this.filtering.buildQuery(),
                        operations: [
                            {'_name': 'getAlert', 'idOrName': this.alertId},
                            {'_name': 'observables'}
                        ],
                        extraData: ['seen'],
                        onUpdate: function() {
                            //self.resetSelection();

                        }
                    });
                };

                this.search = function () {
                    self.load();
                    self.filtering.storeContext();
                };

                this.addFilterValue = function (field, value) {
                    this.filtering.addFilterValue(field, value);
                    this.search();
                };

                this.filterBy = function(field, value) {
                    this.filtering.clearFilters()
                        .then(function() {
                            self.addFilterValue(field, value);
                        });
                };

                // this.filter = function () {
                //     self.filtering.filter().then(this.applyFilters);
                // };

                this.clearFilters = function () {
                    this.filtering.clearFilters()
                        .then(self.search);
                };

                this.removeFilter = function (index) {
                    self.filtering.removeFilter(index)
                        .then(self.search);
                };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/alert/observable-list.component.html',
            bindings: {
                alertId: '<',
                onListLoad: '&'
            }
        });
})();
