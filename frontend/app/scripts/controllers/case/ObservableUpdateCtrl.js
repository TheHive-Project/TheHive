(function() {
    'use strict';
    angular.module('theHiveControllers').controller('ObservableUpdateCtrl',
        function($scope, $uibModalInstance, TagSrv, TaxonomyCacheSrv, selection) {
            var self = this;

            this.selection = selection;
            this.state = {
                all: false,
                enableTlp: false,
                enableIoc: false,
                enableSighted: false,
                enableIgnoreSimilarity: false,
                enableAddTags: false,
                enableRemoveTags: false
            };

            this.activeTlp = 'active';
            this.params = {
                ioc: false,
                sighted: false,
                ignoreSimilarity: false,
                tlp: 2,
                addTagNames: '',
                removeTagNames: ''
            };

            this.toggleAll = function() {

                this.state.all = !this.state.all;

                this.state.enableTlp = this.state.all;
                this.state.enableIoc = this.state.all;
                this.state.enableSighted = this.state.all;
                this.state.enableIgnoreSimilarity = this.state.all;
                this.state.enableAddTags = this.state.all;
                this.state.enableRemoveTags = this.state.all;
            };

            this.categorizeObservables = function() {
                var data = {
                    withTags: [],
                    withoutTags: []
                };

                _.each(this.selection, function(item) {
                    if(item.tags.length > 0) {
                        data.withTags.push(item);
                    } else {
                        data.withoutTags.push(item);
                    }
                });

                return data;
            };

            this.buildOperations = function(postData) {
                var flags = _.pick(postData, 'ioc', 'sighted', 'ignoreSimilarity', 'tlp');

                // Handle updates without tag changes
                if(!postData.addTags && !postData.removeTags) {
                    return [
                        {
                            ids: _.pluck(this.selection, '_id'),
                            patch: flags
                        }
                    ];
                }

                // Handle update with tag changes
                var input = this.categorizeObservables();
                var operations = [];
                if(input.withoutTags.length > 0) {
                    var tags = (postData.addTags || []).filter(function(i) {
                        return (postData.removeTags || []).indexOf(i) === -1;
                    });

                    operations.push({
                        ids: _.pluck(input.withoutTags, '_id'),
                        patch: _.extend({}, flags ,{
                            tags: _.unique(tags)
                        })
                    });
                }

                if(input.withTags.length > 0) {
                    _.each(input.withTags, function(observable) {
                        tags = observable.tags.concat(postData.addTags || []).filter(function(i) {
                            return (postData.removeTags || []).indexOf(i) === -1;
                        });

                        operations.push({
                            ids: [observable._id],
                            patch: _.extend({}, flags ,{
                                tags: _.unique(tags)
                            })
                        });
                    });
                }

                return operations;
            };

            this.save = function() {

                var postData = {};

                if(this.state.enableIoc) {
                    postData.ioc = this.params.ioc;
                }

                if(this.state.enableSighted) {
                    postData.sighted = this.params.sighted;
                }

                if(this.state.enableIgnoreSimilarity) {
                    postData.ignoreSimilarity = this.params.ignoreSimilarity;
                }

                if(this.state.enableTlp) {
                    postData.tlp = this.params.tlp;
                }

                if(this.state.enableAddTags) {
                    postData.addTags = _.pluck(this.params.addTags, 'text');
                }

                if(this.state.enableRemoveTags) {
                    postData.removeTags = _.pluck(this.params.removeTags, 'text');
                }

                $uibModalInstance.close(this.buildOperations(postData));
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.getTags = function(query) {
                return TagSrv.getTags(query);
            };

            self.fromTagLibrary = function(field) {
                TaxonomyCacheSrv.openTagLibrary()
                    .then(function(tags){
                        if(field === 'add') {
                            self.params.addTags = (self.params.addTags || []).concat(tags);
                            self.toggleAddTags();
                        } else if (field === 'remove') {
                            self.params.removeTags = (self.params.removeTags || []).concat(tags);
                            self.toggleRemoveTags();
                        }
                    })
            };

            this.toogleIoc = function() {
                this.params.ioc = !this.params.ioc;
                this.state.enableIoc = true;
            };

            this.toogleSighted = function() {
                this.params.sighted = !this.params.sighted;
                this.state.enableSighted = true;
            };

            this.toogleIgnoreSimilarity = function() {
                this.params.ignoreSimilarity = !this.params.ignoreSimilarity;
                this.state.enableIgnoreSimilarity = true;
            };

            this.toggleTlp = function(value) {
                this.params.tlp = value;
                this.activeTlp = 'active';
                this.state.enableTlp = true;
            };

            this.toggleAddTags = function() {
                this.state.enableAddTags = true;
            };

            this.toggleRemoveTags = function() {
                this.state.enableRemoveTags = true;
            };

            $scope.$watchCollection('$dialog.params.addTags', function(value) {
                self.params.addTagNames = _.pluck(value, 'text').join(',');
            });

            $scope.$watchCollection('$dialog.params.removeTags', function(value) {
                self.params.removeTagNames = _.pluck(value, 'text').join(',');
            });
        }
    );
})();
