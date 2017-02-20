(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('MispListCtrl', function($scope, $q, $state, $uibModal, MispSrv, AlertSrv, FilteringSrv) {
            var self = this;

            self.list = [];
            self.selection = [];
            self.menu = {
                follow: false,
                unfollow: false,
                markAsRead: false,
                selectAll: false
            };
            self.filtering = new FilteringSrv('misp-section', {
                defaults: {
                    showFilters: false,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-publishDate']
                },
                defaultFilter: {
                    eventStatus: {
                        field: 'eventStatus',
                        label: 'Status',
                        value: [{
                            text: 'New'
                        }, {
                            text: 'Update'
                        }],
                        filter: '(eventStatus:"New" OR eventStatus:"Update")'
                    }
                },
                filterDefs: {
                    keyword: {
                        field: 'keyword',
                        type: 'string',
                        defaultValue: []
                    },
                    eventStatus: {
                        field: 'eventStatus',
                        type: 'list',
                        defaultValue: [],
                        label: 'Status'
                    },
                    tags: {
                        field: 'tags',
                        type: 'list',
                        defaultValue: [],
                        label: 'Tags'
                    },
                    org: {
                        field: 'org',
                        type: 'list',
                        defaultValue: [],
                        label: 'Source'
                    },
                    info: {
                        field: 'info',
                        type: 'string',
                        defaultValue: '',
                        label: 'Title'
                    },
                    publishDate: {
                        field: 'publishDate',
                        type: 'date',
                        defaultValue: {
                            from: null,
                            to: null
                        },
                        label: 'Publish Date'
                    }
                }
            });
            self.filtering.initContext('list');
            self.searchForm = {
                searchQuery: self.filtering.buildQuery() || ''
            };

            $scope.$watch('misp.list.pageSize', function (newValue) {
                self.filtering.setPageSize(newValue);
            });

            this.toggleStats = function () {
                this.filtering.toggleStats();
            };

            this.toggleFilters = function () {
                this.filtering.toggleFilters();
            };

            self.follow = function(event) {
                var fn = angular.noop;

                if (event.follow === true) {
                    fn = MispSrv.unfollow;
                } else {
                    fn = MispSrv.follow;
                }

                fn(event.id).then(function( /*data*/ ) {
                    self.list.update();
                }, function(response) {
                    AlertSrv.error('MispListCtrl', response.data, response.status);
                });
            };

            self.bulkFollow = function(follow) {
                var ids = _.pluck(self.selection, 'id');
                var fn = angular.noop;

                if (follow === true) {
                    fn = MispSrv.follow;
                } else {
                    fn = MispSrv.unfollow;
                }

                var promises = _.map(ids, function(id) {
                    return fn(id);
                });

                $q.all(promises).then(function( /*response*/ ) {
                    self.list.update();

                    AlertSrv.log('The selected events have been ' + (follow ? 'followed' : 'unfollowed'), 'success');
                }, function(response) {
                    AlertSrv.error('MispListCtrl', response.data, response.status);
                });
            };

            self.import = function(event) {
                $uibModal.open({
                    templateUrl: 'views/partials/misp/event.dialog.html',
                    controller: 'MispEventCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        event: event
                    }
                });
            };

            self.bulkImport = function() {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/misp/bulk.import.dialog.html',
                    controller: 'MispBulkImportCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        events: function() {
                            return self.selection;
                        }
                    }
                });

                modalInstance.result.then(function(data) {
                    self.list.update();

                    if (data.length === 1) {
                        $state.go('app.case.details', {
                            caseId: data[0].id
                        });
                    }

                });
            };

            self.bulkIgnore = function() {
                var ids = _.pluck(self.selection, 'id');

                var promises = _.map(ids, function(id) {
                    return MispSrv.ignore(id);
                });

                $q.all(promises).then(function( /*response*/ ) {
                    self.list.update();
                    AlertSrv.log('The selected events have been ignored', 'success');
                }, function(response) {
                    AlertSrv.error('MispListCtrl', response.data, response.status);
                });
            };

            self.resetSelection = function() {
                if (self.menu.selectAll) {
                    self.selectAll();
                } else {
                    self.selection = [];
                    self.menu.selectAll = false;
                    self.updateMenu();
                }
            };

            self.ignore = function(event) {
                MispSrv.ignore(event.id).then(function( /*data*/ ) {
                    self.list.update();
                });
            };

            self.load = function() {
                var config = {
                    scope: $scope,
                    filter: self.searchForm.searchQuery !== '' ? {
                        _string: self.searchForm.searchQuery
                    } : '',
                    loadAll: false,
                    sort: self.filtering.context.sort,
                    pageSize: self.filtering.context.pageSize,
                };

                self.list = MispSrv.list(config, self.resetSelection);
            };

            self.cancel = function() {
                self.modalInstance.close();
            };

            self.updateMenu = function() {
                var temp = _.uniq(_.pluck(self.selection, 'follow'));

                self.menu.unfollow = temp.length === 1 && temp[0] === true;
                self.menu.follow = temp.length === 1 && temp[0] === false;


                temp = _.uniq(_.pluck(self.selection, 'eventStatus'));

                self.menu.markAsRead = temp.indexOf('Ignore') === -1;
            };

            self.select = function(event) {
                if (event.selected) {
                    self.selection.push(event);
                } else {
                    self.selection = _.reject(self.selection, function(item) {
                        return item.id === event.id;
                    });
                }

                self.updateMenu();

            };

            self.selectAll = function() {
                var selected = self.menu.selectAll;
                _.each(self.list.values, function(item) {
                    item.selected = selected;
                });

                if (selected) {
                    self.selection = self.list.values;
                } else {
                    self.selection = [];
                }

                self.updateMenu();

            };

            this.filter = function () {
                self.filtering.filter().then(this.applyFilters);
            };

            this.applyFilters = function () {
                self.searchForm.searchQuery = self.filtering.buildQuery();
                self.search();
            };

            this.clearFilters = function () {
                self.filtering.clearFilters().then(this.applyFilters);
            };

            this.addFilter = function (field, value) {
                self.filtering.addFilter(field, value).then(this.applyFilters);
            };

            this.removeFilter = function (field) {
                self.filtering.removeFilter(field).then(this.applyFilters);
            };

            this.search = function () {
                this.list.filter = {
                    _string: this.searchForm.searchQuery
                };

                this.list.update();
            };
            this.addFilterValue = function (field, value) {
                var filterDef = self.filtering.filterDefs[field];
                var filter = self.filtering.activeFilters[field];
                var date;

                if (filter && filter.value) {
                    if (filterDef.type === 'list') {
                        if (_.pluck(filter.value, 'text').indexOf(value) === -1) {
                            filter.value.push({
                                text: value
                            });
                        }
                    } else if (filterDef.type === 'date') {
                        date = moment(value);
                        self.filtering.activeFilters[field] = {
                            value: {
                                from: date.hour(0).minutes(0).seconds(0).toDate(),
                                to: date.hour(23).minutes(59).seconds(59).toDate()
                            }
                        };
                    } else {
                        filter.value = value;
                    }
                } else {
                    if (filterDef.type === 'list') {
                        self.filtering.activeFilters[field] = {
                            value: [{
                                text: value
                            }]
                        };
                    } else if (filterDef.type === 'date') {
                        date = moment(value);
                        self.filtering.activeFilters[field] = {
                            value: {
                                from: date.hour(0).minutes(0).seconds(0).toDate(),
                                to: date.hour(23).minutes(59).seconds(59).toDate()
                            }
                        };
                    } else {
                        self.filtering.activeFilters[field] = {
                            value: value
                        };
                    }
                }

                this.filter();
            };

            this.filterByStatus = function(status) {
                self.filtering.clearFilters()
                    .then(function(){
                        self.addFilterValue('eventStatus', status);
                    });
            };

            this.sortBy = function(sort) {
                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };

            this.getStatuses = function(query) {
                return MispSrv.statuses(query);
            };

            this.getSources = function(query) {
                return MispSrv.sources(query);
            };

            self.load();
        });
})();
