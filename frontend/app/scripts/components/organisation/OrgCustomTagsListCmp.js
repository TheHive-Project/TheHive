(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('orgCustomTagsList', {
            controller: function($scope, PaginatedQuerySrv, FilteringSrv, TagSrv, UserSrv, ModalUtilsSrv, NotificationSrv) {
                var self = this;

                self.tags = [];
                self.getUserInfo = UserSrv.getCache;

                this.$onInit = function() {
                    // TODO: FIXME
                    self.filtering = new FilteringSrv('tag', 'custom-tags.list', {
                        version: 'v1',
                        defaults: {
                            showFilters: true,
                            showStats: false,
                            pageSize: 15,
                            sort: ['+predicate']
                        },
                        defaultFilter: []
                    });

                    self.filtering.initContext(self.organisation.name)
                        .then(function() {
                            self.load();

                            $scope.$watch('$vm.list.pageSize', function (newValue) {
                                self.filtering.setPageSize(newValue);
                            });
                        });
                };

                this.load = function() {

                    self.list = new PaginatedQuerySrv({
                        name: 'organisation-custom-tags',
                        version: 'v1',
                        skipStream: true,
                        sort: self.filtering.context.sort,
                        loadAll: false,
                        pageSize: self.filtering.context.pageSize,
                        filter: this.filtering.buildQuery(),
                        operations: [
                            {
                                '_name': 'freetags'
                            }
                        ],
                        extraData: ['usage'],
                        onFailure: function(err) {
                            if(err && err.status === 400) {
                                self.filtering.resetContext();
                                self.load();
                            }
                        }
                    });
                };

                self.deleteTag = function (tag) {
                    ModalUtilsSrv.confirm('Remove free tag', 'Are you sure you want to delete this tag?', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    })
                        .then(function () {
                            return TagSrv.removeTag(tag._id);
                        })
                        .then(function () {
                            NotificationSrv.success('Tag [' + tag.predicate + '] removed successfully');

                            self.load();

                            $scope.$emit('freetags:refresh');
                        });
                };

                self.updateColour = function(id, colour) {
                    TagSrv.updateTag(id, {colour: colour})
                        .then(function(/*response*/) {
                            NotificationSrv.success('Tag colour updated successfully');
                        })
                        .catch(function(err) {
                            NotificationSrv.error('Tag list', err.data, err.status);
                        })
                }

                self.updateTag = function(id, value) {
                    TagSrv.updateTag(id, {predicate: value})
                        .then(function(/*response*/) {
                            NotificationSrv.success('Tag value updated successfully');
                        })
                        .catch(function(err) {
                            NotificationSrv.error('Tag list', err.data, err.status);
                        })
                }

                // Filtering
                this.toggleFilters = function () {
                    this.filtering.toggleFilters();
                };

                this.filter = function () {
                    self.filtering.filter().then(this.applyFilters);
                };

                this.clearFilters = function () {
                    this.filtering.clearFilters()
                        .then(self.search);
                };

                this.removeFilter = function (index) {
                    self.filtering.removeFilter(index)
                        .then(self.search);
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
                    self.filtering.clearFilters()
                        .then(function(){
                            self.addFilterValue(field, value);
                        });
                };

                this.sortBy = function(sort) {
                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };

                this.sortByField = function(field) {
                    var context = this.filtering.context;
                    var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                    var sort = null;

                    if(currentSort.substr(1) !== field) {
                        sort = ['+' + field];
                    } else {
                        sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
                    }

                    self.list.sort = sort;
                    self.list.update();
                    self.filtering.setSort(sort);
                };
            },
            controllerAs: '$vm',
            templateUrl: 'views/components/org/custom-tags/tag-list.html',
            bindings: {
                organisation: '<',
                templates: '=',
                fields: '<',
                onReload: '&',
                onEdit: '&'
            }
        });
})();
