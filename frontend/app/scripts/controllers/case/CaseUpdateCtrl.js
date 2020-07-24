(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseUpdateCtrl',
        function($scope, $uibModalInstance, TagSrv, selection) {
            var self = this;

            this.selection = selection;
            this.state = {
                all: false,
                enableTlp: false,
                enablePap: false,
                enableSeverity: false,
                enableAddTags: false,
                enableRemoveTags: false
            };

            this.activeTlp = 'active';
            this.activePap = 'active';
            this.activeSeverity = true;

            this.params = {
                ioc: false,
                tlp: 2,
                pap: 2,
                severity: 2,
                addTagNames: '',
                removeTagNames: ''
            };

            this.toggleAll = function() {

                this.state.all = !this.state.all;

                this.state.enableTlp = this.state.all;
                this.state.enablePap = this.state.all;
                this.state.enableSeverity = this.state.all;
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
                var flags = _.pick(postData, 'pap', 'tlp', 'severity');

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
                    _.each(input.withTags, function(caze) {
                        tags = caze.tags.concat(postData.addTags || []).filter(function(i) {
                            return (postData.removeTags || []).indexOf(i) === -1;
                        });

                        operations.push({
                            ids: [caze._id],
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

                if(this.state.enableTlp) {
                    postData.tlp = this.params.tlp;
                }

                if(this.state.enablePap) {
                    postData.pap = this.params.pap;
                }

                if(this.state.enableSeverity) {
                    postData.severity = this.params.severity;
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
                return TagSrv.fromCases(query);
            };

            this.toggleTlp = function(value) {
                this.params.tlp = value;
                this.activeTlp = 'active';
                this.state.enableTlp = true;
            };

            this.togglePap = function(value) {
                this.params.pap = value;
                this.activePap = 'active';
                this.state.enablePap = true;
            };

            this.toggleSeverity = function(value) {
                this.params.severity = value;
                this.activeSeverity = true;
                this.state.enableSeverity = true;
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
