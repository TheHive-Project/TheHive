(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('alertSimilarCaseList', {
            controller: function($scope, FilteringSrv, PaginatedQuerySrv, CaseResolutionStatus) {
                var self = this;

                self.CaseResolutionStatus = CaseResolutionStatus;

                self.similarityFilters = {
                    fTitle: undefined
                };

                self.matchFilters = {
                    fMatches: []
                };

                self.rateFilters = {
                    fObservables: undefined,
                    fIocs: undefined
                };

                self.sortField = '-sCreatedAt';
                self.matches = [];
                self.filteredCases = [];

                self.pagination = {
                    pageSize: 10,
                    currentPage: 1
                };

                self.$onInit = function() {
                    this.filtering = new FilteringSrv('case', 'alert.dialog.similar-cases', {
                        version: 'v1',
                        defaults: {
                            showFilters: true,
                            showStats: false,
                            pageSize: 2,
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
                        //pageSize: self.filtering.context.pageSize,
                        operations: [
                            {'_name': 'getAlert', 'idOrName': this.alertId},
                            {'_name': 'similarCases', 'caseFilter': this.filtering.buildQuery()}
                        ],
                        onUpdate: function(data) {
                            _.each(data, function(item) {
                                item.fTitle = item.case.title;
                                item.fMatches = _.keys(item.observableTypes);
                                item.fObservables = Math.floor((item.similarObservableCount / item.observableCount) * 100);
                                item.fIocs = Math.floor((item.similarIocCount / item.iocCount) * 100) || 0;

                                item.sCreatedAt = item.case._createdAt;
                            });

                            self.matches = _.uniq(_.flatten(_.map(data, function(item){
                                return _.keys(item.observableTypes);
                            }))).sort();
                        }
                    });
                };

                self.merge = function(caseId) {
                    this.onMergeIntoCase({
                        caseId: caseId
                    });
                };

                // Frontend filter methods
                this.clearLocalFilters = function() {
                    self.similarityFilters = {
                        fTitle: undefined
                    };

                    self.matchFilters = {
                        fMatches: []
                    };

                    self.rateFilters = {
                        fObservables: undefined,
                        fIocs: undefined
                    };
                };

                this.greaterThan = function(prop){
                    return function(item){
                        return !self.rateFilters[prop] || item[prop] >= self.rateFilters[prop];
                    };
                };

                this.matchFilter = function() {
                    return function(item){
                        return !self.matchFilters.fMatches || self.matchFilters.fMatches.length === 0 ||
                            _.intersection(self.matchFilters.fMatches, item.fMatches).length > 0;
                    };
                };

                // Filtering methods
                this.toggleFilters = function () {
                    this.filtering.toggleFilters();
                };

                this.search = function () {
                    self.load();
                    self.filtering.storeContext();
                };

                this.addFilterValue = function (field, value) {
                    self.filtering.addFilterValue(field, value);
                    self.search();
                };

                /// Clear all filters
                this.clearFilters = function () {
                    self.filtering.clearFilters()
                        .then(self.search);
                };

                // Remove a filter
                this.removeFilter = function (index) {
                    self.filtering.removeFilter(index)
                        .then(self.search);
                };

                this.filterBy = function(field, value) {
                    self.filtering.clearFilters()
                        .then(function(){
                            self.addFilterValue(field, value);
                        });
                };

                this.filterSimilarities = function(data) {
                    return data;
                };

                this.sortByField = function(field) {
                    var sort = null;

                    if(this.sortField.substr(1) !== field) {
                        sort = '+' + field;
                    } else {
                        sort = (this.sortField === '+' + field) ? '-'+field : '+'+field;
                    }

                    this.sortField = sort;
                };


            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/alert/similar-case-list.component.html',
            bindings: {
                alertId: '<',
                onListLoad: '&',
                onMergeIntoCase: '&'
            }
        });
})();
