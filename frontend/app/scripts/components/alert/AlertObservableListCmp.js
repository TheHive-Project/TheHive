(function () {
    'use strict';

    angular.module('theHiveComponents')
        .component('alertObservableList', {
            controller: function ($scope, FilteringSrv, QuerySrv, PaginatedQuerySrv) {
                var self = this;

                self.$onInit = function () {
                    this.filtering = new FilteringSrv('observable', 'alert.dialog.observables', {
                        version: 'v1',
                        defaults: {
                            showFilters: false,
                            showStats: false,
                            pageSize: 15,
                            sort: ['-startDate']
                        },
                        defaultFilter: []
                    });

                    self.filtering.initContext(this.alertId)
                        .then(function () {
                            self.load();

                            $scope.$watch('$cmp.list.pageSize', function (newValue) {
                                self.filtering.setPageSize(newValue);
                            });
                        });

                    QuerySrv.query(
                        'v1',
                        [{ '_name': 'countAlertObservable', 'alertId': self.alertId }],
                        {
                            params: {
                                name: 'alert-all-observables.count'
                            }
                        })
                        .then(function (response) {
                            self.observablesCount = response.data;
                            self.onListLoad({ count: self.observablesCount });
                        });
                };

                this.load = function () {
                    this.list = new PaginatedQuerySrv({
                        name: 'alert-observables',
                        skipStream: true,
                        version: 'v1',
                        sort: self.filtering.context.sort,
                        loadAll: false,
                        limitedCount: true,
                        pageSize: self.filtering.context.pageSize,
                        filter: this.filtering.buildQuery(),
                        operations: [
                            { '_name': 'getAlert', 'idOrName': this.alertId },
                            { '_name': 'observables' }
                        ],
                        extraData: ['seen'],
                        onUpdate: function () { }
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

                this.filterBy = function (field, value) {
                    this.filtering.clearFilters()
                        .then(function () {
                            self.addFilterValue(field, value);
                        });
                };

                // this.filter = function () {
                //     self.filtering.filter().then(this.applyFilters);
                // };
                //

                // Filtering methods
                this.toggleFilters = function () {
                    this.filtering.toggleFilters();
                };

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
